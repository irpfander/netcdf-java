/*
 * Copyright (c) 1998-2018 John Caron and University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */
package ucar.nc2.ft.point.collection;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.Formatter;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import thredds.inventory.TimedCollection;
import ucar.ma2.StructureData;
import ucar.nc2.Attribute;
import ucar.nc2.AttributeContainer;
import ucar.nc2.VariableSimpleIF;
import ucar.nc2.constants.FeatureType;
import ucar.nc2.ft.DsgFeatureCollection;
import ucar.nc2.ft.FeatureDatasetFactoryManager;
import ucar.nc2.ft.FeatureDatasetPoint;
import ucar.nc2.ft.PointFeature;
import ucar.nc2.ft.PointFeatureCollection;
import ucar.nc2.ft.PointFeatureCollectionIterator;
import ucar.nc2.ft.PointFeatureIterator;
import ucar.nc2.ft.StationTimeSeriesFeature;
import ucar.nc2.ft.StationTimeSeriesFeatureCollection;
import ucar.nc2.ft.point.PointIteratorAbstract;
import ucar.nc2.ft.point.StationFeature;
import ucar.nc2.ft.point.StationHelper;
import ucar.nc2.ft.point.StationTimeSeriesCollectionImpl;
import ucar.nc2.ft.point.StationTimeSeriesFeatureImpl;
import ucar.nc2.time.CalendarDateRange;
import ucar.nc2.time.CalendarDateUnit;
import ucar.unidata.geoloc.LatLonRect;
import ucar.unidata.geoloc.Station;

/**
 * StationTimeSeries composed of a collection of individual StationTimeSeries. "Composite" pattern.
 *
 * @author caron
 * @since May 19, 2009
 */
public class CompositeStationCollection extends StationTimeSeriesCollectionImpl implements UpdateableCollection {
  private static final Logger log = LoggerFactory.getLogger(CompositeStationCollection.class);

  private final TimedCollection dataCollection;
  protected List<VariableSimpleIF> dataVariables;
  private AttributeContainer globalAttributes;

  CompositeStationCollection(String name, CalendarDateUnit timeUnit, String altUnits, TimedCollection dataCollection) {
    super(name, timeUnit, altUnits);
    this.dataCollection = dataCollection;
    TimedCollection.Dataset td = dataCollection.getPrototype();
    if (td == null)
      throw new RuntimeException("No datasets in the collection");
  }

  @Override
  protected StationHelper createStationHelper() throws IOException {
    TimedCollection.Dataset td = dataCollection.getPrototype();
    if (td == null)
      throw new RuntimeException("No datasets in the collection");

    Formatter errlog = new Formatter();
    try (FeatureDatasetPoint openDataset =
        (FeatureDatasetPoint) FeatureDatasetFactoryManager.open(FeatureType.STATION, td.getLocation(), null, errlog)) {
      if (openDataset == null)
        throw new IllegalStateException("Cant open FeatureDatasetPoint " + td.getLocation());

      StationHelper stationHelper = new StationHelper();

      List<DsgFeatureCollection> fcList = openDataset.getPointFeatureCollectionList();
      StationTimeSeriesCollectionImpl openCollection = (StationTimeSeriesCollectionImpl) fcList.get(0);
      List<StationFeature> stns = openCollection.getStationFeatures();

      for (StationFeature stnFeature : stns) {
        stationHelper.addStation(new CompositeStationFeature(stnFeature, timeUnit, altUnits,
            stnFeature.getFeatureData(), this.dataCollection));
      }

      dataVariables = openDataset.getDataVariables();
      globalAttributes = openDataset.attributes();

      return stationHelper;
    }
  }

  public List<VariableSimpleIF> getDataVariables() {
    getStationHelper(); // dataVariables gets initialized when stationHelper does. Bit of a kludge.
    return dataVariables;
  }

  public AttributeContainer attributes() {
    return globalAttributes;
  }

  /** @deprecated use attributes() */
  @Deprecated
  public List<Attribute> getGlobalAttributes() {
    getStationHelper(); // globalAttributes gets initialized when stationHelper does. Bit of a kludge.
    return ImmutableList.copyOf(globalAttributes);
  }

  @Override
  public CalendarDateRange update() throws IOException {
    return dataCollection.update();
  }

  // Must override default subsetting implementation for efficiency
  // StationTimeSeriesFeatureCollection

  @Override
  public StationTimeSeriesFeatureCollection subset(List<StationFeature> stations) {
    if (stations == null) {
      return this;
    } else {
      // List<StationFeature> subset = getStationHelper().getStationFeatures(stations);
      return new CompositeStationCollectionSubset(this, stations);
    }
  }

  @Override
  public StationTimeSeriesFeatureCollection subset(ucar.unidata.geoloc.LatLonRect boundingBox) {
    if (boundingBox == null) {
      return this;
    } else {
      List<StationFeature> subset = getStationHelper().getStationFeatures(boundingBox);
      return new CompositeStationCollectionSubset(this, subset);
    }
  }

  /*
   * @Override
   * public StationTimeSeriesFeature getStationFeature(Station s) throws IOException {
   * StationFeature stnFeature = getStationHelper().getStationFeature(s);
   * return new CompositeStationFeature(stnFeature, timeUnit, altUnits, stnFeature.getFeatureData(), dataCollection);
   * }
   */

  @Override
  public PointFeatureCollection flatten(LatLonRect boundingBox, CalendarDateRange dateRange) {
    TimedCollection subsetCollection = (dateRange != null) ? dataCollection.subset(dateRange) : dataCollection;
    return new CompositeStationCollectionFlattened(getName(), getTimeUnit(), getAltUnits(), boundingBox, dateRange,
        subsetCollection);

    // return flatten(stationHelper.getStations(boundingBox), dateRange, null);
  }

  @Override
  public PointFeatureCollection flatten(List<String> stations, CalendarDateRange dateRange,
      List<VariableSimpleIF> varList) {
    TimedCollection subsetCollection = (dateRange != null) ? dataCollection.subset(dateRange) : dataCollection;
    return new CompositeStationCollectionFlattened(getName(), getTimeUnit(), getAltUnits(), stations, dateRange,
        varList, subsetCollection);
  }


  private static class CompositeStationCollectionSubset extends CompositeStationCollection {
    private final CompositeStationCollection from;
    private final List<StationFeature> stationFeats;

    private CompositeStationCollectionSubset(CompositeStationCollection from, List<StationFeature> stationFeats) {
      super(from.getName(), from.getTimeUnit(), from.getAltUnits(), from.dataCollection);
      this.from = Preconditions.checkNotNull(from, "from == null");

      Preconditions.checkArgument(stationFeats != null && !stationFeats.isEmpty(),
          "stationFeats == null || stationFeats.isEmpty(): %s", stationFeats);
      this.stationFeats = stationFeats;
    }

    @Override
    protected StationHelper createStationHelper() throws IOException {
      StationHelper stationHelper = new StationHelper();

      for (StationFeature stationFeat : this.stationFeats) {
        stationHelper.addStation(new CompositeStationFeature(stationFeat, timeUnit, altUnits,
            stationFeat.getFeatureData(), from.dataCollection));
      }

      return stationHelper;
    }
  }

  //////////////////////////////////////////////////////////
  // the iterator over StationTimeSeriesFeature objects
  // problematic - since each station will independently iterate over the datasets.
  // betterto use the flatten() method, then reconstitute the station with getStation(pointFeature)

  @Override
  public PointFeatureCollectionIterator getPointFeatureCollectionIterator() {

    // an anonymous class iterating over the stations
    return new PointFeatureCollectionIterator() {
      final Iterator<Station> stationIter = getStationHelper().getStations().iterator();

      @Override
      public boolean hasNext() {
        return stationIter.hasNext();
      }

      @Override
      public PointFeatureCollection next() {
        return (PointFeatureCollection) stationIter.next();
      }

      @Override
      public void close() {}
    };
  }

  // the StationTimeSeriesFeature

  private static class CompositeStationFeature extends StationTimeSeriesFeatureImpl {
    private final TimedCollection collForFeature;
    private final StructureData sdata;

    CompositeStationFeature(StationFeature s, CalendarDateUnit timeUnit, String altUnits, StructureData sdata,
        TimedCollection collForFeature) {
      super(s, timeUnit, altUnits, -1);
      this.sdata = sdata;
      this.collForFeature = collForFeature;
      CalendarDateRange cdr = collForFeature.getDateRange();
      if (cdr != null) {
        getInfo();
        info.setCalendarDateRange(cdr);
      }
    }

    // an iterator over the observations for this station

    @Override
    public PointFeatureIterator getPointFeatureIterator() {
      return new CompositeStationFeatureIterator();
    }

    /*
     * public StationTimeSeriesFeature subset(DateRange dateRange) throws IOException {
     * return subset(CalendarDateRange.of(dateRange)); // Handles dateRange == null.
     * }
     */

    @Override
    public StationTimeSeriesFeature subset(CalendarDateRange dateRange) {
      if (dateRange == null)
        return this;

      // Extract the collection members that intersect dateRange. For example, if we have:
      // collForFeature == CollectionManager{
      // Dataset{location='foo_20141015.nc', dateRange=2014-10-15T00:00:00Z - 2014-10-16T00:00:00Z}
      // Dataset{location='foo_20141016.nc', dateRange=2014-10-16T00:00:00Z - 2014-10-17T00:00:00Z}
      // Dataset{location='foo_20141017.nc', dateRange=2014-10-17T00:00:00Z - 2014-10-18T00:00:00Z}
      // Dataset{location='foo_20141018.nc', dateRange=2014-10-18T00:00:00Z - 2014-10-19T00:00:00Z}
      // }
      // and we want to subset it with:
      // dateRange == 2014-10-16T12:00:00Z - 2014-10-17T12:00:00Z
      // the result will be:
      // collectionSubset == CollectionManager{
      // Dataset{location='foo_20141016.nc', dateRange=2014-10-16T00:00:00Z - 2014-10-17T00:00:00Z}
      // Dataset{location='foo_20141017.nc', dateRange=2014-10-17T00:00:00Z - 2014-10-18T00:00:00Z}
      // }
      TimedCollection collectionSubset = collForFeature.subset(dateRange);

      // Create a new CompositeStationFeature from the subsetted collection.
      CompositeStationFeature compStnFeatSubset =
          new CompositeStationFeature(stationFeature, getTimeUnit(), getAltUnits(), sdata, collectionSubset);

      // We're not done yet! While compStnFeatSubset has been limited to only include datasets that intersect dateRange,
      // it'll often be the case that those datasets contain some times that we don't want. In the example above,
      // we only want:
      // dateRange == 2014-10-16T12:00:00Z - 2014-10-17T12:00:00Z
      // but the range of compStnFeatSubset is:
      // 2014-10-16T00:00:00Z - 2014-10-18T00:00:00Z
      // We don't want the first or last 12 hours! So, wrap in a StationFeatureSubset to do per-feature filtering.
      // Note that the TimedCollection.subset() call above is still worth doing, as it limits the number of features
      // we must iterate over.
      return new StationFeatureSubset(compStnFeatSubset, dateRange);
    }

    @Nonnull
    @Override
    public StructureData getFeatureData() {
      return sdata;
    }

    @Override
    @Nullable
    public PointFeatureCollection subset(LatLonRect boundingBox, CalendarDateRange dateRange) {
      if (boundingBox != null) {
        if (!boundingBox.contains(stationFeature.getStation().getLatLon()))
          return null;
        if (dateRange == null)
          return this;
      }
      return subset(dateRange);
    }

    // the iterator over PointFeature - an iterator over iterators, one for each dataset
    private class CompositeStationFeatureIterator extends PointIteratorAbstract {
      private final Iterator<TimedCollection.Dataset> iter;
      private FeatureDatasetPoint currentDataset;
      private PointFeatureIterator pfIter;
      private boolean finished;

      CompositeStationFeatureIterator() {
        iter = collForFeature.getDatasets().iterator();
      }

      private PointFeatureIterator getNextIterator() throws IOException {
        if (!iter.hasNext())
          return null;
        TimedCollection.Dataset td = iter.next();
        Formatter errlog = new Formatter();
        currentDataset = (FeatureDatasetPoint) FeatureDatasetFactoryManager.open(FeatureType.STATION, td.getLocation(),
            null, errlog);
        if (currentDataset == null)
          throw new IllegalStateException("Cant open FeatureDatasetPoint " + td.getLocation());

        List<DsgFeatureCollection> fcList = currentDataset.getPointFeatureCollectionList();
        StationTimeSeriesFeatureCollection stnCollection = (StationTimeSeriesFeatureCollection) fcList.get(0);
        StationFeature sf = stnCollection.findStationFeature(getName());
        if (sf == null) {
          log.debug("CompositeStationFeatureIterator dataset: {} missing station {}", td.getLocation(), getName());
          // close (or just release if cache is enabled) current dataset and check for station in
          // next dataset in collection
          currentDataset.close();
          return getNextIterator();
        }

        StationTimeSeriesFeature stnFeature = stnCollection.getStationTimeSeriesFeature(sf);
        if (CompositeDatasetFactory.debug)
          System.out.printf("CompositeStationFeatureIterator open dataset: %s for %s%n", td.getLocation(),
              sf.getStation().getName());
        return stnFeature.getPointFeatureIterator();
      }

      @Override
      public boolean hasNext() {
        try {
          if (pfIter == null) {
            pfIter = getNextIterator();
            if (pfIter == null) {
              close();
              return false;
            }
          }

          if (!pfIter.hasNext()) {
            pfIter.close();
            currentDataset.close();
            if (CompositeDatasetFactory.debug)
              System.out.printf("CompositeStationFeatureIterator close dataset: %s%n", currentDataset.getLocation());
            pfIter = getNextIterator();
            return hasNext();
          }

          return true;
        } catch (IOException ioe) {
          throw new RuntimeException(ioe);
        }
      }

      @Override
      public PointFeature next() {
        PointFeature pf = pfIter.next();
        calcBounds(pf);
        return pf;
      }

      @Override
      public void close() {
        if (finished)
          return;

        if (pfIter != null)
          pfIter.close();

        if (currentDataset != null)
          try {
            currentDataset.close();
            if (CompositeDatasetFactory.debug)
              System.out.printf("CompositeStationFeatureIterator close dataset: %s%n", currentDataset.getLocation());
          } catch (IOException e) {
            throw new RuntimeException(e);
          }

        finishCalcBounds();
        finished = true;
      }
    }
  }
}
