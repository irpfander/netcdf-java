---
title: Coverage datasets
last_updated: 2019-11-02
sidebar: userguide_sidebar
toc: false
permalink: coverage_feature.html
---

{% include image.html file="featuretypes/CoverageDataset.svg" alt="Coverage Dataset" caption="" %}

##  Overview

**Coverage Datasets** are collections of Coverage Features such as *Grid*, *Fmrc*, and *Swath*.
`FeatureDatasetCoverage` are containers for `CoverageCollections`:

~~~java
public static void FeatureDatasetCoverageTutorial(){
        public class FeatureDatasetCoverage implements FeatureDataset, Closeable {
            public List<CoverageCollection> getCoverageCollections();
            public CoverageCollection getSingleCoverageCollection();
            public CoverageCollection findCoverageDataset(FeatureType type);
            public CoverageCollection findCoverageDataset(String name);
            public FeatureType getFeatureType();
        }
    }
~~~

`FeatureDatasetCoverage` extends `FeatureDataset`, and contains *discovery metadata* for search and discovery.
A `CoverageCollection` can only have a single `HorizCoordSys`, `Calendar`, and `FeatureType`.
A single endpoint (eg GRIB collection) often has more than one `HorizCoordSys` or `Type` (eg Best and TwoD), and so has multiple collections.
When there are multiple types, `getFeatureType()` returns the generic `FeatureType.COVERAGE`. Otherwise, it will return the specific type.

* `Grid`: all coordinates are one dimensional, aka *separable*.
* `Fmrc`: Both a *runtime* and *time* coordinate exist. The time coordinate may be 2D, or it may be a 1D *time offset* coordinate.
* `Swath`: has 2D lat/lon & time exists but not independent
* `Curvilinear:` has 2D lat/lon & time independent if it exists

A `CoverageCollection` contains coverages, coordinate systems, coordinate axes, and coordinate transforms.
All of the coverages in a collections are on the same horizontal grid, and have the same datetime Calendar.



~~~java
public class CoverageCollection implements Closeable, CoordSysContainer {
            private List<CoordSysSet> wireObjectsTogether(List<Coverage> coverages);
            private HorizCoordSys wireHorizCoordSys();
            public AttributeContainer attributes();

            public String getName();
            public LatLonRect getLatlonBoundingBox();
            public ProjectionRect getProjBoundingBox();
            public CalendarDateRange getCalendarDateRange();
            public ucar.nc2.time.Calendar getCalendar();
            public Iterable<Coverage> getCoverages();
            public int getCoverageCount();
            public FeatureType getCoverageType();
            public List<CoordSysSet> getCoverageSets();
            public List<CoverageCoordSys> getCoordSys();
            public List<CoverageTransform> getCoordTransforms();
            public List<CoverageCoordAxis> getCoordAxes();
            public HorizCoordSys getHorizCoordSys();
            public CoverageReader getReader();

            public Coverage findCoverage(String name);
            public Coverage findCoverageByAttribute(String attName, String attValue);
            public CoverageCoordSys findCoordSys(String name);
            public CoverageCoordAxis findCoordAxis(String name);
            public CoverageTransform findCoordTransform(String name);

            public String toString();
            public void close();
        }
~~~


### CoordSysSet

A `CoordSysSet` simply groups all the `Coverages` that belong to the same `CoverageCoordSys`:

~~~java
public class CoordSysSet {
  public CoordSysSet(CoverageCoordSys coordSys);
  void addCoverage(Coverage cov);
  public CoverageCoordSys getCoordSys();
  public List<Coverage> getCoverages();
}
~~~

### CoverageCoordSys

{% include image.html file="featuretypes/CoverageCoordSys.svg" alt="Coverage Coordinate Systems" caption="" %}

A `CoverageCoordSys` represents the _Coordinate System_ for _Coverages_.

~~~java
public class CoverageCoordSys {
    public static String makeCoordSysName(List<String> axisNames);
    public boolean isConstantForecast();
    void setIsConstantForecast(boolean isConstantForecast);
    void setImmutable();
    public void setDataset(CoordSysContainer dataset);
    public void setHorizCoordSys(HorizCoordSys horizCoordSys);
    public HorizCoordSys makeHorizCoordSys();

    public String getName();
    public List<String> getTransformNames();
    public List<CoverageTransform> getTransforms();
    public CoverageTransform getHorizTransform();
    public HorizCoordSys getHorizCoordSys();
    public FeatureType getCoverageType();
    public List<String> getAxisNames();

    public String toString();
    public CoverageCoordAxis getXAxis();
    public CoverageCoordAxis getYAxis();
    public CoverageCoordAxis getZAxis();
    public CoverageCoordAxis getTimeAxis();
    public CoverageCoordAxis getAxis(AxisType type);
    public CoverageCoordAxis getAxis(String axisName);
    public List<CoverageCoordAxis> getAxes();

    public int[] getShape();
    public Projection getProjection();
    public CoverageTransform findCoordTransform(String transformName);
    public CoverageCoordAxis findCoordAxis(String axisName);

    public boolean isTime2D(CoverageCoordAxis axis);
    public List<CoverageCoordAxis> getDependentAxes(CoverageCoordAxis indAxis);
}
~~~

### CoverageCoordAxis
A `CoverageCoordAxis` represents the *Coordinate Axis* for *Coverages*. It is an abstract superclass which implements `Comparable<CoverageCoordAxis>`.

~~~java
public abstract class CoverageCoordAxis implements Comparable<CoverageCoordAxis> {
    public enum Spacing {
        regularPoint, // regularly spaced points (start, end, npts)
        irregularPoint, // irregular spaced points (values, npts)
        regularInterval, // regular contiguous intervals (start, end, npts)
        contiguousInterval, // irregular contiguous intervals (values, npts)
        discontiguousInterval // irregular discontiguous spaced intervals (values, npts)
        }
    public enum DependenceType {
        independent, // has its own dimension, is a coordinate variable, eg x(x)
        dependent, // aux coordinate, eg reftime(time) or time_bounds(time);
        scalar, // eg reftime
        twoD, // lat(x,y)
        fmrcReg, // time(reftime, hourOfDay)
        dimension // swath(scan, scanAcross)
    }
    public int compareTo(CoverageCoordAxis o);
    public abstract CoverageCoordAxis copy();
    public String toString();
    public int[] getShape();
    public Range getRange();
    public RangeIterator getRangeIterator();
    public void toString(Formatter f, Indent indent);
    public String getSummary();
    public double convert(CalendarDate date);
    public CalendarDate makeDate(double value);
    
    public CalendarDateRange getDateRange();
    public double getOffsetInTimeUnits(CalendarDate start, CalendarDate end);
    public CalendarDate makeDateInTimeUnits(CalendarDate start, double addTo);
    public CalendarDate getRefDate();
    public Calendar getCalendar();
    public CalendarDateUnit getCalendarDateUnit();
    protected void loadValuesIfNeeded();
    public double[] getValues();
    }
}
~~~


### CoverageTransform

A `CoverageTransform` contains parameters for horizontal or vertical transforms.


~~~java
public class CoverageTransform  {
    public CoverageTransform(String name, AttributeContainer attributes, boolean isHoriz);
    public boolean isHoriz();
    public Projection getProjection();
    public String getName();
    public String toString();
    public AttributeContainer attributes();
    public static Projection makeProjection(CoverageTransform gct, Formatter errInfo);
}
~~~
## Classification

Classifications and requirements for Coverage Datasets.
* Must have lat/lon or have x,y and projection
* x and y both have rank <= 2
* x and y both have size > 1; eliminates some miscoded point data
* x and y have at least 2 dimensions between them (eliminates point data)
* A runtime axis must be scalar or one-dimensional
* Time may be 0, 1 or 2 dimensional
* If time is 2D and runtime exists, first time dimension must agree with runtime
* Other coordinates, dependent or independent (ie has another dimension) are okay

