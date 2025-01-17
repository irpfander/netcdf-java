/*
 * Copyright (c) 1998-2020 John Caron and University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */
package ucar.nc2.grib.collection;

import javax.annotation.Nonnull;
import ucar.nc2.ft2.coverage.SubsetParams;
import ucar.nc2.grib.GdsHorizCoordSys;

/** @deprecated FeatureDatasets will move to legacy in ver7, this class will not be public. */
@Deprecated
class GribReaderRecord implements Comparable<GribReaderRecord> {
  int resultIndex; // index into the result array
  final GribCollectionImmutable.Record record;
  final GdsHorizCoordSys hcs;
  SubsetParams validation;

  GribReaderRecord(int resultIndex, GribCollectionImmutable.Record record, GdsHorizCoordSys hcs) {
    this.resultIndex = resultIndex;
    this.record = record;
    this.hcs = hcs;
  }

  @Override
  public int compareTo(@Nonnull GribReaderRecord o) {
    int r = Integer.compare(record.fileno, o.record.fileno);
    if (r != 0)
      return r;
    return Long.compare(record.pos, o.record.pos);
  }

  // debugging
  public void show(GribCollectionImmutable gribCollection) {
    String dataFilename = gribCollection.getFilename(record.fileno);
    System.out.printf(" fileno=%d filename=%s startPos=%d%n", record.fileno, dataFilename, record.pos);
  }
}
