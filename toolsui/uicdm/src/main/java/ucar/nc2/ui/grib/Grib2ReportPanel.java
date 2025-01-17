/*
 * Copyright (c) 1998-2019 University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */

package ucar.nc2.ui.grib;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import thredds.featurecollection.FeatureCollectionConfig;
import thredds.inventory.CollectionUpdateType;
import thredds.inventory.MCollection;
import thredds.inventory.MFile;
import ucar.nc2.Attribute;
import ucar.nc2.NetcdfFile;
import ucar.nc2.NetcdfFiles;
import ucar.nc2.dataset.CoordinateAxis1D;
import ucar.nc2.dataset.NetcdfDataset;
import ucar.nc2.dataset.NetcdfDatasets;
import ucar.nc2.dt.GridCoordSystem;
import ucar.nc2.dt.GridDatatype;
import ucar.nc2.dt.grid.GridDataset;
import ucar.nc2.grib.GribData;
import ucar.nc2.grib.GribStatType;
import ucar.nc2.grib.GribTables;
import ucar.nc2.grib.GribUtils;
import ucar.nc2.grib.collection.Grib;
import ucar.nc2.grib.collection.GribCdmIndex;
import ucar.nc2.grib.collection.GribCollectionImmutable;
import ucar.nc2.grib.coord.TimeCoordIntvDateValue;
import ucar.nc2.grib.grib2.*;
import ucar.nc2.grib.grib2.table.Grib2Tables;
import ucar.nc2.grib.grib2.table.WmoParamTable;
import ucar.nc2.ui.ReportPanel;
import ucar.nc2.internal.util.Counters;
import ucar.unidata.io.RandomAccessFile;
import ucar.util.prefs.PreferencesExt;
import java.io.*;
import java.util.*;

/**
 * Run through collections of Grib 2 files and make reports
 *
 * @author caron
 * @since Dec 13, 2010
 */
public class Grib2ReportPanel extends ReportPanel {
  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Grib2ReportPanel.class);

  public enum Report {
    checkTables, localUseSection, uniqueTemplates, duplicatePds, drsSummary, gdsSummary, pdsSummary, pdsProblems, idProblems, timeCoord, rename, copyCompress, gribIndex
  }

  public Grib2ReportPanel(PreferencesExt prefs) {
    super(prefs);
  }

  @Override
  public Object[] getOptions() {
    return Grib2ReportPanel.Report.values();
  }

  @Override
  protected void doReport(Formatter f, Object option, MCollection dcm, boolean useIndex, boolean eachFile,
      boolean extra) throws IOException {
    switch ((Report) option) {
      case checkTables:
        doCheckTables(f, dcm, useIndex);
        break;
      case localUseSection:
        doLocalUseSection(f, dcm, useIndex);
        break;
      case uniqueTemplates:
        doUniqueTemplates(f, dcm, useIndex);
        break;
      case duplicatePds:
        doDuplicatePds(f, dcm, useIndex);
        break;
      case drsSummary:
        doDrsSummary(f, dcm, useIndex, eachFile, extra);
        break;
      case gdsSummary:
        doGdsSummary(f, dcm, extra);
        break;
      case pdsSummary:
        doPdsSummary(f, dcm, eachFile);
        break;
      case pdsProblems:
        doPdsProblems(f, dcm, useIndex);
        break;
      case idProblems:
        doIdProblems(f, dcm, useIndex);
        break;
      case timeCoord:
        doTimeCoord(f, dcm, useIndex);
        break;
      case rename:
        doRename(f, dcm, useIndex);
        break;
      case copyCompress:
        doCopyCompress(f, dcm, useIndex, eachFile, extra);
        break;
      case gribIndex:
        doGribIndex(f, dcm, eachFile);
        break;
    }
  }

  /////////////////////////////////////////////////////////////////

  private void doGribIndex(Formatter f, MCollection dcm, boolean eachFile) throws IOException {
    Counters counters = new Counters();
    counters.add("GDS");
    counters.add("GDShashes");

    // must open collection again without gbx filtering
    try (MCollection dcm2 = getCollectionUnfiltered(spec, f)) {

      for (MFile mfile : dcm2.getFilesSorted()) {
        String path = mfile.getPath();
        f.format(" %s%n", path);
        doGribIndex(f, mfile, counters, eachFile);
      }
    }

    counters.show(f);
  }

  private void doGribIndex(Formatter fm, MFile ff, Counters counters, boolean eachFile) {
    String path = ff.getPath();
    Grib2Index g2idx = new Grib2Index();
    g2idx.readIndex(path, 0, thredds.inventory.CollectionUpdateType.nocheck);
    counters.count("GDS", g2idx.getGds().size());

    // count unique hash
    Set<Integer> gdsHash = new HashSet<>();
    for (Grib2SectionGridDefinition gdss : g2idx.getGds()) {
      gdsHash.add(gdss.getGDS().hashCode());
    }
    counters.count("GDShashes", gdsHash.size());
    if (eachFile) {
      fm.format("   count=%d countHash=%d%n", g2idx.getGds().size(), gdsHash.size());
    }
  }

  ///////////////////////////////////////////////
  String dir = "C:/tmp/bzip/";

  private void doCopyCompress(Formatter f, MCollection dcm, boolean useIndex, boolean eachFile, boolean extra)
      throws IOException {
    f.format("Copy and Compress selected files%n");
    Counters counters = new Counters();
    counters.add("Nbits");

    long totalOrg = 0;
    long totalZip = 0;

    for (MFile mfile : dcm.getFilesSorted()) {
      f.format("------- %s%n", mfile.getPath());
      long orgSize = mfile.getLength();
      totalOrg += orgSize;
      File fileOut = new File(dir + mfile.getName() + ".bzip2");

      try (RandomAccessFile raf = new RandomAccessFile(mfile.getPath(), "r");
          OutputStream fout = new BufferedOutputStream(new FileOutputStream(fileOut), 100 * 1000)) {

        int count = 0;
        Grib2RecordScanner scan = new Grib2RecordScanner(raf);
        while (scan.hasNext()) {
          ucar.nc2.grib.grib2.Grib2Record gr = scan.next();
          doCopyCompress(f, gr, raf, fout, counters);
          if (count++ % 100 == 0)
            System.out.printf("%s%n", count);
        }
      }
      long zipSize = fileOut.length();
      totalZip += zipSize;
      double r = (orgSize == 0) ? 0 : ((double) zipSize) / orgSize;
      f.format("  org=%d zip=%d ratio=%f%n", orgSize, zipSize, r);
    }
    double r = totalOrg != 0 ? ((double) totalZip) / totalOrg : 0;
    f.format("  org=%d zip=%d ratio=%f%n", totalOrg, totalZip, r);

    counters.show(f);
  }

  /*
   * https://www.unidata.ucar.edu/software/netcdf/docs/BestPractices.html
   * Packed Data Values
   * 
   * Packed data is stored in a netCDF file by limiting precision and using a smaller data type than the original data,
   * for example, packing double-precision (64-bit) values into short (16-bit) integers. The C-based netCDF libraries do
   * not do the packing and unpacking. (The netCDF Java library will do automatic unpacking when the VariableEnhanced
   * Interface is used. For details see EnhancedScaleMissing).
   * 
   * Each variable with packed data has two attributes called scale_factor and add_offset, so that the packed data may
   * be read and unpacked using the formula:
   * unpacked_data_value = packed_data_value * scale_factor + add_offset
   * 
   * The type of the stored variable is the packed data type, typically byte, short or int.
   * The type of the scale_factor and add_offset attributes should be the type that you want the unpacked data to be,
   * typically float or double.
   * To avoid introducing a bias into the unpacked values due to truncation when packing, the data provider should round
   * to the nearest integer rather than just truncating towards zero before writing the data:
   * packed_data_value = nint((unpacked_data_value - add_offset) / scale_factor)
   * 
   * Depending on whether the packed data values are intended to be interpreted by the reader as signed or unsigned
   * integers, there are alternative ways for the data provider to compute the scale_factor and add_offset attributes.
   * In either case, the formulas above apply for unpacking and packing the data.
   * 
   * A conventional way to indicate whether a byte, short, or int variable is meant to be interpreted as unsigned, even
   * for the netCDF-3 classic model that has no external unsigned integer type, is by providing the special variable
   * attribute _Unsigned with value "true". However, most existing data for which packed values are intended to be
   * interpreted as unsigned are stored without this attribute, so readers must be aware of packing assumptions in this
   * case. In the enhanced netCDF-4 data model, packed integers may be declared to be of the appropriate unsigned type.
   * 
   * Let n be the number of bits in the packed type, and assume dataMin and dataMax are the minimum and maximum values
   * that will be used for a variable to be packed.
   * 
   * If the packed values are intended to be interpreted as signed integers (the default assumption for classic model
   * data), you may use:
   * 
   * scale_factor =(dataMax - dataMin) / (2^n - 1)
   * add_offset = dataMin + 2n - 1 * scale_factor
   * 
   * If the packed values are intended to be interpreted as unsigned (for example, when read in the C interface using
   * the nc_get_var_uchar() function), use:
   * 
   * scale_factor =(dataMax - dataMin) / (2^n - 1)
   * add_offset = dataMin
   * 
   * In either the signed or unsigned case, an alternate formula may be used for the add_offset and scale_factor packing
   * parameters that reserves a packed value for a special value, such as an indicator of missing data. For example, to
   * reserve the minimum packed value (-2n - 1) for use as a special value in the case of signed packed values:
   * 
   * scale_factor =(dataMax - dataMin) / (2^n - 2)
   * add_offset = (dataMax + dataMin) / 2
   * 
   * If the packed values are unsigned, then the analogous formula that reserves 0 as the packed form of a special value
   * would be:
   * 
   * scale_factor =(dataMax - dataMin) / (2^n - 2)
   * add_offset = dataMin - scale_factor
   * 
   * Example, packing 32-bit floats into 16-bit shorts:
   * variables:
   * short data( z, y, x);
   * data:scale_offset = 34.02f;
   * data:add_offset = 1.54f;
   * The units attribute applies to unpacked values.
   */

  private void doCopyCompress(Formatter f, ucar.nc2.grib.grib2.Grib2Record gr, RandomAccessFile raf, OutputStream out,
      Counters counters) throws IOException {
    float[] data = gr.readData(raf);

    Grib2SectionDataRepresentation drss = gr.getDataRepresentationSection();
    drss.getDrs(raf);

    // calc scale/offset
    GribData.Info info = gr.getBinaryDataInfo(raf);
    int nbits = info.numberOfBits;
    counters.count("Nbits", nbits);

    int width = (2 << (nbits - 1)) - 1;
    // f.format(" nbits = %d%n", nbits);
    // f.format(" width = %d (0x%s) %n", width2, Long.toHexString(width2));

    float dataMin = Float.MAX_VALUE;
    float dataMax = -Float.MAX_VALUE;
    for (float fd : data) {
      dataMin = Math.min(dataMin, fd);
      dataMax = Math.max(dataMax, fd);
    }
    // f.format(" dataMin = %f%n", dataMin);
    // f.format(" dataMax = %f%n", dataMax);
    // f.format(" range = %f%n", (dataMax - dataMin));

    // scale_factor =(dataMax - dataMin) / (2^n - 1)
    // add_offset = dataMin + 2^(n-1) * scale_factor

    // float scale_factor = (dataMax - dataMin) / width2;
    // float add_offset = dataMin + width2 * scale_factor / 2;

    float scale_factor = (dataMax - dataMin) / (width - 2);
    float add_offset = dataMin - scale_factor;

    // f.format(" scale_factor = %f%n", scale_factor);
    // f.format(" add_offset = %f%n", add_offset);

    // unpacked_data_value = packed_data_value * scale_factor + add_offset
    // packed_data_value = nint((unpacked_data_value - add_offset) / scale_factor)

    /*
     * compressedSize = out.size();
     * f.format(" compressedSize = %d%n", compressedSize);
     * f.format(" compressedRatio = %f%n", (float) compressedSize / (n*nbits/8));
     * f.format(" ratio with grib = %f%n", (float) compressedSize / bean1.getDataLength());
     */
  }


  ///////////////////////////////////////////////


  private void doCheckTables(Formatter f, MCollection dcm, boolean useIndex) throws IOException {
    f.format("Check Grib-2 Parameter Tables%n");
    int[] accum = new int[4];

    for (MFile mfile : dcm.getFilesSorted()) {
      f.format("%n %s%n", mfile.getPath());
      doCheckTables(mfile, f, accum);
    }

    f.format("%nGrand total=%d not operational = %d local = %d missing = %d%n", accum[0], accum[1], accum[2], accum[3]);
  }

  private void doCheckTables(MFile ff, Formatter fm, int[] accum) throws IOException {
    int local = 0;
    int miss = 0;
    int nonop = 0;
    int total = 0;

    try (GridDataset ncfile = GridDataset.open(ff.getPath())) {
      for (GridDatatype dt : ncfile.getGrids()) {
        String currName = dt.getName();
        total++;

        Attribute att = dt.findAttributeIgnoreCase("Grib_Parameter");
        if (att != null && att.getLength() == 3) {
          int discipline = (Integer) att.getValue(0);
          int category = (Integer) att.getValue(1);
          int number = (Integer) att.getValue(2);
          if ((category > 191) || (number > 191)) {
            fm.format("  local parameter (%d %d %d) = %s units=%s %n", discipline, category, number, currName,
                dt.getUnitsString());
            local++;
            continue;
          }

          GribTables.Parameter entry = WmoParamTable.getParameter(discipline, category, number);
          if (entry == null) {
            fm.format("  missing from WMO table (%d %d %d) = %s units=%s %n", discipline, category, number, currName,
                dt.getUnitsString());
            miss++;
            continue;
          }

          if (!entry.getOperationalStatus().equalsIgnoreCase("Operational")) {
            fm.format("  %s parameter = %s (%d %d %d) %n", entry.getOperationalStatus(), currName, discipline, category,
                number);
            nonop++;
          }
        }

      }
    }
    fm.format("total files=%d not operational = %d local = %d missing = %d%n", total, nonop, local, miss);
    accum[0] += total;
    accum[1] += nonop;
    accum[2] += local;
    accum[3] += miss;
  }

  ///////////////////////////////////////////////

  private void doLocalUseSection(Formatter f, MCollection dcm, boolean useIndex) throws IOException {
    f.format("Show Local Use Section%n");

    for (MFile mfile : dcm.getFilesSorted()) {
      f.format(" %s%n", mfile.getPath());
      doLocalUseSection(mfile, f, useIndex);
    }
  }

  private void doLocalUseSection(MFile mf, Formatter f, boolean useIndex) throws IOException {
    f.format("File = %s%n", mf);

    Grib2Index index = createIndex(mf, f);
    if (index == null)
      return;

    for (Grib2Record gr : index.getRecords()) {
      Grib2SectionLocalUse lus = gr.getLocalUseSection();
      if (lus == null || lus.getRawBytes() == null)
        f.format(" %10d == none%n", gr.getDataSection().getStartingPosition());
      else
        f.format(" %10d == %s%n", gr.getDataSection().getStartingPosition(), Arrays.toString(lus.getRawBytes()));
    }
  }

  private Grib2Index createIndex(MFile mf, Formatter f) throws IOException {
    String path = mf.getPath();
    Grib2Index index = new Grib2Index();
    if (!index.readIndex(path, mf.getLastModified())) {
      // make sure its a grib2 file
      try (RandomAccessFile raf = new RandomAccessFile(path, "r")) {
        if (!Grib2RecordScanner.isValidFile(raf))
          return null;
        index.makeIndex(path, raf);
      }
    }
    return index;
  }

  ///////////////////////////////////////////////

  // Look through the collection and find what GDS and PDS templates are used.
  private void doUniqueTemplates(Formatter f, MCollection dcm, boolean useIndex) throws IOException {
    f.format("Show Unique GDS and PDS templates%n");

    Map<Integer, FileList> gdsSet = new HashMap<>();
    Map<Integer, FileList> pdsSet = new HashMap<>();
    Map<Integer, FileList> drsSet = new HashMap<>();
    for (MFile mfile : dcm.getFilesSorted()) {
      f.format(" %s%n", mfile.getPath());
      doUniqueTemplates(mfile, gdsSet, pdsSet, drsSet, f);
    }

    List<FileList> sorted = new ArrayList<>(gdsSet.values());
    Collections.sort(sorted);
    for (FileList gdsl : sorted) {
      f.format("%nGDS %s template= %d %n", gdsl.name, gdsl.template);
      for (FileCount fc : gdsl.fileList) {
        f.format("  %5d %s %n", fc.countRecords, fc.f.getPath());
      }
    }

    List<FileList> sortedPds = new ArrayList<>(pdsSet.values());
    Collections.sort(sortedPds);
    for (FileList pdsl : sortedPds) {
      f.format("%n===================================================%n");
      f.format("%nPDS %s template= %d %n", pdsl.name, pdsl.template);
      for (FileCount fc : pdsl.fileList) {
        f.format("  %5d %s %n", fc.countRecords, fc.f.getPath());
      }
    }

    List<FileList> sortedDrs = new ArrayList<>(drsSet.values());
    Collections.sort(sortedDrs);
    for (FileList pdsl : sortedDrs) {
      f.format("%n===================================================%n");
      f.format("%nDRS %s template= %d %n", pdsl.name, pdsl.template);
      for (FileCount fc : pdsl.fileList) {
        f.format("  %5d %s %n", fc.countRecords, fc.f.getPath());
      }
    }
  }

  private void doUniqueTemplates(MFile mf, Map<Integer, FileList> gdsSet, Map<Integer, FileList> pdsSet,
      Map<Integer, FileList> drsSet, Formatter f) {
    String path = mf.getPath();
    Grib2Index gribIndex = new Grib2Index();
    boolean ok = gribIndex.readIndex(path, 0, thredds.inventory.CollectionUpdateType.nocheck);
    if (!ok) {
      f.format("**Cant open %s%n", path);
      return;
    }

    for (Grib2Record gr : gribIndex.getRecords()) {
      int template = gr.getGDSsection().getGDSTemplateNumber();
      gdsSet.computeIfAbsent(template, k -> new FileList(k, gr.getGDSsection().getGDS().getNameShort()));
      gdsSet.get(template).findAndAdd(mf);

      int pdsTemplate = gr.getPDSsection().getPDSTemplateNumber();
      pdsSet.computeIfAbsent(pdsTemplate, k -> new FileList(k, gr.getPDSsection().getPDS().getClass().getName()));
      pdsSet.get(pdsTemplate).findAndAdd(mf);

      int drsTemplate = gr.getDataRepresentationSection().getDataTemplate();
      drsSet.computeIfAbsent(drsTemplate, k -> new FileList(k, "DRS" + k));
      drsSet.get(drsTemplate).findAndAdd(mf);

    }
  }

  // keep track of all files that use this template
  private static class FileList implements Comparable<FileList> {
    String name;
    int template;
    java.util.List<FileCount> fileList = new ArrayList<>();

    private FileList(int template, String name) {
      this.name = name;
      this.template = template;
    }

    void findAndAdd(MFile f) {
      FileCount which = null;
      for (FileCount fc : fileList) {
        if (fc.f.getPath().equals(f.getPath()))
          which = fc;
      }

      if (which == null) {
        which = new FileCount(f);
        fileList.add(which);
      }
      which.countRecords++;
    }

    @Override
    public int compareTo(FileList o) {
      return template - o.template;
    }
  }

  // track number of records for each file
  private static class FileCount {
    private FileCount(MFile f) {
      this.f = f;
    }

    MFile f;
    int countRecords;
  }

  ///////////////////////////////////////////////

  private int countPDS, countPDSdup;

  private void doDuplicatePds(Formatter f, MCollection dcm, boolean useIndex) throws IOException {
    countPDS = 0;
    countPDSdup = 0;
    f.format("Show Duplicate PDS%n");
    for (MFile mfile : dcm.getFilesSorted()) {
      doDuplicatePds(f, mfile);
    }
    f.format("Total PDS duplicates = %d / %d%n%n", countPDSdup, countPDS);
  }

  private void doDuplicatePds(Formatter f, MFile mfile) throws IOException {
    Set<Long> pdsMap = new HashSet<>();
    int dups = 0;
    int count = 0;

    RandomAccessFile raf = new RandomAccessFile(mfile.getPath(), "r");
    Grib2RecordScanner scan = new Grib2RecordScanner(raf);
    while (scan.hasNext()) {
      ucar.nc2.grib.grib2.Grib2Record gr = scan.next();
      Grib2SectionProductDefinition pds = gr.getPDSsection();
      long crc = pds.calcCRC();
      if (pdsMap.contains(crc))
        dups++;
      else
        pdsMap.add(crc);
      count++;
    }
    raf.close();

    f.format("PDS duplicates = %d / %d for %s%n%n", dups, count, mfile.getPath());
    countPDS += count;
    countPDSdup += dups;
  }

  ///////////////////////////////////////////////
  int total;
  int prob;

  private void doPdsProblems(Formatter f, MCollection dcm, boolean useIndex) throws IOException {
    f.format("Check Grib-2 PDS probability and statistical variables%n");
    total = 0;
    prob = 0;
    for (MFile mfile : dcm.getFilesSorted()) {
      f.format("%n %s%n", mfile.getPath());
      doPdsProblems(f, mfile);
    }
    f.format("problems = %d/%d%n", prob, total);
  }

  private void doPdsProblems(Formatter fm, MFile ff) throws IOException {
    String path = ff.getPath();

    Grib2Index index = createIndex(ff, fm);
    if (index == null)
      return;

    Formatter errlog = new Formatter();
    // make sure ncx gets created for each file.
    try (GribCollectionImmutable gc = GribCdmIndex.openGribCollectionFromDataFile(false, ff,
        CollectionUpdateType.nocheck, new FeatureCollectionConfig(), errlog, logger)) {
      // ??
    }

    try (GridDataset ncfile = GridDataset.open(path + GribCdmIndex.NCX_SUFFIX)) {
      for (GridDatatype dt : ncfile.getGrids()) {
        String currName = dt.getName();
        total++;

        Attribute att = dt.findAttributeIgnoreCase("Grib_Probability_Type");
        if (att != null) {
          fm.format("  %s (PROB) desc=%s %n", currName, dt.getDescription());
          prob++;
        }

        att = dt.findAttributeIgnoreCase(Grib.GRIB_STAT_TYPE);
        if (att != null) {
          if (att.getNumericValue() == null) {
            System.out.printf("HEY expect number for %s%n", att.getName());
          } else {
            int statType = att.getNumericValue().intValue();
            if ((statType == 7) || (statType == 9)) {
              fm.format("  %s (STAT type %s) desc=%s %n", currName, statType, dt.getDescription());
              prob++;
            }
          }
        }

        att = dt.findAttributeIgnoreCase("Grib_Ensemble_Derived_Type");
        if (att != null) {
          if (att.getNumericValue() == null) {
            System.out.printf("HEY expect number for %s%n", att.getName());
          } else {
            int type = att.getNumericValue().intValue();
            if ((type > 9)) {
              fm.format("  %s (DERIVED type %s) desc=%s %n", currName, type, dt.getDescription());
              prob++;
            }
          }
        }

      }
    } catch (Throwable ioe) {
      fm.format("Failed on %s == %s%n", path, ioe.getMessage());
      System.out.printf("Failed on %s%n", path);
      ioe.printStackTrace();

    }
  }

  ///////////////////////////////////////////////////

  private void doPdsSummary(Formatter f, MCollection dcm, boolean eachFile) throws IOException {
    Counters countersAll = new Counters();
    countersAll.add("template");
    countersAll.add("timeUnit");
    countersAll.add("timeOffset");
    countersAll.add("timeIntervalSize");
    countersAll.add("levelType");
    countersAll.add("genProcessType");
    countersAll.add("genProcessId");
    countersAll.add("levelScale");
    countersAll.add("nExtraCoords");

    for (MFile mfile : dcm.getFilesSorted()) {
      f.format("---%s%n", mfile.getPath());
      doPdsSummary(f, mfile, countersAll);

      if (eachFile) {
        countersAll.show(f);
        countersAll.reset();
      }
    }

    f.format("PdsSummary - all files%n");
    if (!eachFile)
      countersAll.show(f);
  }

  private void doPdsSummary(Formatter f, MFile mf, Counters counters) throws IOException {
    boolean showLevel = true;
    boolean showCoords = true;

    Grib2Index index = createIndex(mf, f);
    if (index == null)
      return;

    Grib2Tables cust = null;
    for (ucar.nc2.grib.grib2.Grib2Record gr : index.getRecords()) {
      if (cust == null)
        cust = Grib2Tables.factory(gr);

      Grib2Pds pds = gr.getPDS();
      counters.count("template", pds.getTemplateNumber());
      counters.count("timeUnit", pds.getTimeUnit());
      counters.count("timeOffset", pds.getForecastTime());

      if (pds.isTimeInterval()) {
        int[] intv = cust.getForecastTimeIntervalOffset(gr);
        if (intv != null)
          counters.count("timeIntervalSize", intv[1] - intv[0]);
      }

      counters.count("levelType", pds.getLevelType1());
      if (showLevel && (pds.getLevelType1() == 105)) {
        showLevel = false;
        f.format(" level = 105 : %s%n", mf.getPath());
      }

      int n = pds.getExtraCoordinatesCount();
      counters.count("nExtraCoords", n);
      if (showCoords && (n > 0)) {
        showCoords = false;
        f.format(" ncoords > 0 : %s%n", mf.getPath());
      }

      int ptype = pds.getGenProcessType();
      counters.count("genProcessType", ptype);
      /*
       * if (firstPtype < 0) firstPtype = ptype;
       * else if (firstPtype != ptype && !shutup) {
       * f.format(" getGenProcessType differs in %s %s == %d%n", mf.getPath(), gr.getPDS().getParameterNumber(), ptype);
       * shutup = true;
       * }
       */
      counters.count("genProcessId", pds.getGenProcessId());

      if (pds.getLevelScale1() > 127) {
        if (cust.isLevelUsed(pds.getLevelType1())) {
          f.format(" LevelScale > 127: %s %s == %d%n", mf.getPath(), gr.getPDS().getParameterNumber(),
              pds.getLevelScale1());
          counters.count("levelScale", pds.getLevelScale1());
        }
      }
    }
  }

  ///////////////////////////////////////////////

  private void doIdProblems(Formatter f, MCollection dcm, boolean useIndex) throws IOException {
    f.format("Look for ID Problems%n");

    Counters countersAll = new Counters();
    countersAll.add("discipline");
    countersAll.add("masterTable");
    countersAll.add("localTable");
    countersAll.add("centerId");
    countersAll.add("subcenterId");
    countersAll.add("genProcess");
    countersAll.add("backProcess");
    countersAll.add("significanceOfReference");

    for (MFile mfile : dcm.getFilesSorted()) {
      f.format(" %s%n", mfile.getPath());
      doIdProblems(f, mfile, useIndex, countersAll);
    }

    countersAll.show(f);
  }

  private void doIdProblems(Formatter f, MFile mf, boolean showProblems, Counters countersAll) throws IOException {
    Grib2Index index = createIndex(mf, f);
    if (index == null)
      return;

    // these should be the same for the entire file
    int center = -1;
    int subcenter = -1;
    int master = -1;
    int local = -1;
    int genProcess = -1;
    int backProcess = -1;
    int sigRef = -1;

    for (ucar.nc2.grib.grib2.Grib2Record gr : index.getRecords()) {
      countersAll.count("discipline", gr.getDiscipline());
      countersAll.count("masterTable", gr.getId().getMaster_table_version());
      countersAll.count("localTable", gr.getId().getLocal_table_version());
      countersAll.count("centerId", gr.getId().getCenter_id());
      countersAll.count("subcenterId", gr.getId().getSubcenter_id());
      countersAll.count("genProcess", gr.getPDS().getGenProcessId());
      countersAll.count("backProcess", gr.getPDS().getBackProcessId());
      countersAll.count("significanceOfReference", gr.getId().getSignificanceOfRT());

      if (!showProblems)
        continue;

      if (gr.getDiscipline() == 255) {
        f.format("  bad discipline= ");
        gr.show(f);
        f.format("%n");
      }

      int val = gr.getId().getCenter_id();
      if (center < 0)
        center = val;
      else if (center != val) {
        f.format("  center %d != %d ", center, val);
        gr.show(f);
        f.format(" %s%n", gr.getId());
      }

      val = gr.getId().getSubcenter_id();
      if (subcenter < 0)
        subcenter = val;
      else if (subcenter != val) {
        f.format("  subcenter %d != %d ", subcenter, val);
        gr.show(f);
        f.format(" %s%n", gr.getId());
      }

      val = gr.getId().getMaster_table_version();
      if (master < 0)
        master = val;
      else if (master != val) {
        f.format("  master %d != %d ", master, val);
        gr.show(f);
        f.format(" %s%n", gr.getId());
      }

      val = gr.getId().getLocal_table_version();
      if (local < 0)
        local = val;
      else if (local != val) {
        f.format("  local %d != %d ", local, val);
        gr.show(f);
        f.format(" %s%n", gr.getId());
      }

      val = gr.getPDS().getGenProcessId();
      if (genProcess < 0)
        genProcess = val;
      else if (genProcess != val) {
        f.format("  genProcess %d != %d ", genProcess, val);
        gr.show(f);
        f.format(" %s%n", gr.getId());
      }

      val = gr.getPDS().getBackProcessId();
      if (backProcess < 0)
        backProcess = val;
      else if (backProcess != val) {
        f.format("  backProcess %d != %d ", backProcess, val);
        gr.show(f);
        f.format(" %s%n", gr.getId());
      }

      val = gr.getId().getSignificanceOfRT();
      if (sigRef < 0)
        sigRef = val;
      else if (sigRef != val) {
        f.format("  sigRef %d != %d ", sigRef, val);
        gr.show(f);
        f.format(" %s%n", gr.getId());
      }

    }
  }

  ///////////////////////////////////////////////

  private void doDrsSummary(Formatter f, MCollection dcm, boolean useIndex, boolean eachFile, boolean extra)
      throws IOException {
    f.format("Show Unique DRS Templates%n");
    Counters countersAll = new Counters();
    countersAll.add("DRS_template");
    countersAll.add("BMS indicator");
    countersAll.add("DRS template 40 signed problem");
    countersAll.add("Number_of_Bits");

    for (MFile mfile : dcm.getFilesSorted()) {

      f.format("------- %s%n", mfile.getPath());
      if (useIndex)
        doDrsSummaryIndex(f, mfile, extra, countersAll);
      else
        doDrsSummaryScan(f, mfile, extra, countersAll);

      if (eachFile) {
        countersAll.show(f);
        f.format("%n");
        countersAll.reset();
      }
    }

    if (!eachFile)
      countersAll.show(f);
  }

  private void doDrsSummaryIndex(Formatter f, MFile mf, boolean extra, Counters counters) throws IOException {
    Grib2Index index = createIndex(mf, f);
    if (index == null)
      return;
    long messageSum = 0;
    int nrecords = 0;

    String path = mf.getPath();
    try (RandomAccessFile raf = new RandomAccessFile(path, "r")) {

      for (ucar.nc2.grib.grib2.Grib2Record gr : index.getRecords()) {
        Grib2SectionDataRepresentation drss = gr.getDataRepresentationSection();
        int template = drss.getDataTemplate();
        counters.count("DRS_template", template);
        messageSum += gr.getIs().getMessageLength();
        nrecords++;

        // Grib2SectionBitMap bms = gr.getBitmapSection();
        counters.count("BMS indicator", gr.repeat);

        if (extra && template == 40) { // expensive
          Grib2Drs.Type40 drs40 = gr.readDataTest(raf);
          if (drs40 != null) {
            if (drs40.hasSignedProblem())
              counters.count("DRS template 40 signed problem", 1);
            else
              counters.count("DRS template 40 signed problem", 0);
          }
        }
      }

      float percent = ((float) messageSum) / raf.length();
      f.format("raf length = %d, messageSum = %d, percent = %f nrecords = %d%n", raf.length(), messageSum, percent,
          nrecords);
    }
  }

  private void doDrsSummaryScan(Formatter f, MFile mf, boolean extra, Counters counters) throws IOException {
    RandomAccessFile raf = new RandomAccessFile(mf.getPath(), "r");
    Grib2RecordScanner scan = new Grib2RecordScanner(raf);
    while (scan.hasNext()) {
      ucar.nc2.grib.grib2.Grib2Record gr = scan.next();
      doDrsSummary(gr, raf, extra, counters);
    }
    raf.close();
  }

  private void doDrsSummary(ucar.nc2.grib.grib2.Grib2Record gr, RandomAccessFile raf, boolean extra, Counters counters)
      throws IOException {
    Grib2SectionDataRepresentation drss = gr.getDataRepresentationSection();
    int template = drss.getDataTemplate();
    counters.count("DRS_template", template);

    // Grib2SectionBitMap bms = gr.getBitmapSection();
    counters.count("BMS indicator", gr.repeat);

    GribData.Info info = gr.getBinaryDataInfo(raf);
    counters.count("Number_of_Bits", info.numberOfBits);

    if (extra && template == 40) { // expensive
      Grib2Drs.Type40 drs40 = gr.readDataTest(raf);
      if (drs40 != null) {
        if (drs40.hasSignedProblem())
          counters.count("DRS template 40 signed problem", 1);
        else
          counters.count("DRS template 40 signed problem", 0);
      }
    }
  }

  ///////////////////////////////////////////////

  private void doGdsSummary(Formatter f, MCollection dcm, boolean extra) throws IOException {
    f.format("Show Unique GDS Templates%n");
    Counters counters = new Counters();
    counters.add("template");
    counters.add("scanMode");
    counters.add("scanModeDifference");

    for (MFile mfile : dcm.getFilesSorted()) {
      f.format(" %s%n", mfile.getPath());
      doGdsSummary(f, mfile, counters, extra);
    }

    counters.show(f);
  }

  private void doGdsSummary(Formatter f, MFile mf, Counters counters, boolean extra) throws IOException {
    Grib2Index index = createIndex(mf, f);
    if (index == null)
      return;

    Map<Integer, Grib2SectionGridDefinition> gdssMap = new HashMap<>();
    for (Grib2SectionGridDefinition gdss : index.getGds()) {
      gdssMap.put(gdss.hashCode(), gdss);

      ucar.nc2.grib.grib2.Grib2Gds gds = gdss.getGDS();
      gds.testScanMode(f);

      int template = gdss.getGDSTemplateNumber();
      int scanMode = gds.getScanMode();
      if (extra && GribUtils.scanModeYisPositive(scanMode)) {
        f.format("    template %d with Ypos%n", template);
      }
    }

    for (ucar.nc2.grib.grib2.Grib2Record gr : index.getRecords()) {
      Grib2SectionGridDefinition gdss = gr.getGDSsection();
      Grib2SectionGridDefinition gdssIndex = gdssMap.get(gdss.hashCode());
      ucar.nc2.grib.grib2.Grib2Gds gdsIndex = gdssIndex.getGDS();

      counters.count("template", gdss.getGDSTemplateNumber());
      counters.count("scanMode", gr.getScanMode());
      if (gdsIndex.getScanMode() != gr.getScanMode()) {
        counters.count("scanModeDifference", mf.getName());
      }

    }
  }

  ///////////////////////////////////////////////////////////////////

  private void doTimeCoord(Formatter f, MCollection dcm, boolean useIndex) throws IOException {
    Counters counters = new Counters();
    counters.add("template");
    counters.add("timeUnit");
    counters.add("statType");
    counters.add("NumberTimeIntervals");
    counters.add("TimeIntervalsDiffer");
    counters.add("TimeIntervalsLength");

    int count = 0;
    for (MFile mfile : dcm.getFilesSorted()) {
      f.format(" %s%n", mfile.getPath());
      count += doTimeCoord(f, mfile, counters);
    }

    f.format("total records = %d%n", count);
    counters.show(f);
  }


  private int doTimeCoord(Formatter f, MFile mf, Counters counters) throws IOException {
    boolean showTinvDiffers = false;
    boolean showNint = false;
    boolean shutup = false;

    Grib2Index index = createIndex(mf, f);
    if (index == null)
      return 0;
    Grib2Tables cust = null;

    int count = 0;
    for (ucar.nc2.grib.grib2.Grib2Record gr : index.getRecords()) {
      Grib2Pds pds = gr.getPDS();
      counters.count("template", pds.getTemplateNumber());
      int timeUnit = pds.getTimeUnit();
      counters.count("timeUnit", timeUnit);

      if (pds instanceof Grib2Pds.PdsInterval) {
        Grib2Pds.PdsInterval pdsi = (Grib2Pds.PdsInterval) pds;
        for (Grib2Pds.TimeInterval ti : pdsi.getTimeIntervals()) {
          counters.count("statType", ti.statProcessType);

          if ((ti.timeRangeUnit != timeUnit)
              || (ti.timeIncrementUnit != timeUnit && ti.timeIncrementUnit != 255 && ti.timeIncrement != 0)) {
            counters.count("TimeIntervalsDiffer", ti.timeRangeUnit);
            if (showTinvDiffers) {
              f.format("  TimeInterval has different units timeUnit= %s file=%s%n  ", timeUnit, mf.getName());
              pds.show(f);
              f.format("%n");
            }
          }
        }

        counters.count("NumberTimeIntervals", pdsi.getTimeIntervals().length);
        if (showNint && !shutup && pdsi.getTimeIntervals().length > 1) {
          f.format("  TimeIntervals > 1 = %s file=%s%n  ", getId(gr), mf.getName());
          shutup = true;
        }

        if (cust == null) {
          cust = Grib2Tables.factory(gr);
        }
        double len = cust.getForecastTimeIntervalSizeInHours(pds);
        counters.count("TimeIntervalsLength", (int) len);
        int[] intv = cust.getForecastTimeIntervalOffset(gr);
        if (intv != null && (intv[0] == 0) && (intv[1] == 0)) {
          f.format("  TimeInterval [0,0] = %s file=%s%n  ", getId(gr), mf.getName());
        }

        TimeCoordIntvDateValue intvDateOld = cust.getForecastTimeIntervalOld(gr);
        TimeCoordIntvDateValue intvDate = cust.getForecastTimeInterval(gr);
        TimeCoordIntvDateValue intvDateNew = cust.getForecastTimeIntervalNew(gr);
        if (!intvDateOld.equals(intvDateNew)) {
          f.format("  intvDateOld %s%n", intvDateOld);
          f.format("  intvDate    %s%n", intvDate);
          f.format("  intvDateNew %s%n%n", intvDateNew);
        }
        counters.count("TimeIntvDatesDiffer", intvDateOld.equals(intvDateNew) ? 0 : 1);
        counters.count("TimeIntvDatesStillDiffer", intvDate.equals(intvDateNew) ? 0 : 1);
      }

      count++;
    }

    return count;
  }

  String getId(Grib2Record gr) {
    Grib2SectionIndicator is = gr.getIs();
    Grib2Pds pds = gr.getPDS();
    return is.getDiscipline() + "-" + pds.getParameterCategory() + "-" + pds.getParameterNumber();
  }

  ///////////////////////////////////////////////////////////////////////////////////

  private void doRename(Formatter f, MCollection dcm, boolean useIndex) throws IOException {
    f.format("CHECK Grib-2 Names: Old vs New for collection %s%n", dcm.getCollectionName());

    List<VarName> varNames = new ArrayList<>(3000);
    Map<String, List<String>> gridsAll = new HashMap<>(1000); // old -> list<new>
    int countExactMatch = 0;
    int countExactMatchIg = 0;
    int countOldVars = 0;

    for (MFile mfile : dcm.getFilesSorted()) {
      f.format("%n%s%n", mfile.getPath());
      Map<Integer, GridMatch> gridsNew = getGridsNew(mfile, f);
      Map<Integer, GridMatch> gridsOld = getGridsOld(mfile, f);

      // look for exact match on name
      Set<String> namesNew = new HashSet<>(gridsNew.size());
      for (GridMatch gm : gridsNew.values())
        namesNew.add(gm.grid.getFullName());
      for (GridMatch gm : gridsOld.values()) {
        if (namesNew.contains(gm.grid.getFullName()))
          countExactMatch++;
        countOldVars++;
      }

      // look for exact match on hashcode
      for (GridMatch gm : gridsNew.values()) {
        GridMatch match = gridsOld.get(gm.hashCode());
        if (match != null) {
          gm.match = match;
          match.match = gm;
        }
      }

      // look for alternative match
      for (GridMatch gm : gridsNew.values()) {
        if (gm.match == null) {
          GridMatch match = altMatch(gm, gridsOld.values());
          if (match != null) {
            gm.match = match;
            match.match = gm;
          }
        }
      }

      // print out match
      f.format("%n");
      List<GridMatch> listNew = new ArrayList<>(gridsNew.values());
      Collections.sort(listNew);
      for (GridMatch gm : listNew) {
        f.format(" %s%n", gm.grid.findAttributeIgnoreCase(Grib.VARIABLE_ID_ATTNAME));
        f.format(" %s (%d)%n", gm.grid.getFullName(), gm.hashCode());
        if (gm.match != null) {
          boolean exact = gm.match.grid.getFullName().equals(gm.grid.getFullName());
          boolean exactIg = !exact && gm.match.grid.getFullName().equalsIgnoreCase(gm.grid.getFullName());
          if (exactIg)
            countExactMatchIg++;
          String status = exact ? " " : exactIg ? "**" : " *";
          f.format("%s%s (%d)%n", status, gm.match.grid.getFullName(), gm.match.hashCode());
        }
        f.format("%n");
      }

      // print out missing
      f.format("%nMISSING MATCHES IN NEW%n");
      List<GridMatch> list = new ArrayList<>(gridsNew.values());
      Collections.sort(list);
      for (GridMatch gm : list) {
        if (gm.match == null)
          f.format(" %s (%s) == %s%n", gm.grid.getFullName(), gm.show(), gm.grid.getDescription());
      }
      f.format("%nMISSING MATCHES IN OLD%n");
      List<GridMatch> listOld = new ArrayList<>(gridsOld.values());
      Collections.sort(listOld);
      for (GridMatch gm : listOld) {
        if (gm.match == null)
          f.format(" %s (%s)%n", gm.grid.getFullName(), gm.show());
      }

      // add to gridsAll to track old -> new mapping
      for (GridMatch gmOld : listOld) {
        String key = gmOld.grid.getShortName();
        List<String> newGrids = gridsAll.computeIfAbsent(key, k -> new ArrayList<>());
        if (gmOld.match != null) {
          String keyNew = gmOld.match.grid.getShortName();
          if (!newGrids.contains(keyNew))
            newGrids.add(keyNew);
        }
      }

      // add matches to VarNames
      for (GridMatch gmOld : listOld) {
        if (gmOld.match == null) {
          f.format("MISSING %s (%s)%n", gmOld.grid.getFullName(), gmOld.show());
          continue;
        }
        Attribute att = gmOld.match.grid.findAttributeIgnoreCase(Grib.VARIABLE_ID_ATTNAME);
        String varId = att == null ? "" : att.getStringValue();
        varNames.add(new VarName(mfile.getName(), gmOld.grid.getShortName(), gmOld.match.grid.getShortName(), varId));
      }

    }

    // show old -> new mapping
    f.format("%nOLD -> NEW MAPPINGS%n");
    List<String> keys = new ArrayList<>(gridsAll.keySet());
    int total = keys.size();
    int dups = 0;
    Collections.sort(keys);
    for (String key : keys) {
      f.format(" OLD %s%n", key);
      List<String> newGrids = gridsAll.get(key);
      Collections.sort(newGrids);
      if (newGrids.size() > 1)
        dups++;
      for (String newKey : newGrids)
        f.format(" NEW %s%n", newKey);
      f.format("%n");
    }

    f.format("Exact matches=%d  Exact ignore case=%d  totalOldVars=%d%n", countExactMatch, countExactMatchIg,
        countOldVars);
    f.format("Number with more than one map=%d total=%d%n", dups, total);

    // old -> new mapping xml table
    if (!useIndex) {
      Element rootElem = new Element("gribVarMap");
      Document doc = new Document(rootElem);
      rootElem.setAttribute("collection", dcm.getCollectionName());

      String currentDs = null;
      Element dsElem = null;
      for (VarName vn : varNames) {
        if (!vn.dataset.equals(currentDs)) {
          dsElem = new Element("dataset");
          rootElem.addContent(dsElem);
          dsElem.setAttribute("name", vn.dataset);
          currentDs = vn.dataset;
        }
        Element param = new Element("param");
        dsElem.addContent(param);
        param.setAttribute("oldName", vn.oldVar);
        param.setAttribute("newName", vn.newVar);
        param.setAttribute("varId", vn.varId);
      }

      FileOutputStream fout = new FileOutputStream("C:/tmp/grib2VarMap.xml");
      XMLOutputter fmt = new XMLOutputter(Format.getPrettyFormat());
      fmt.output(doc, fout);
      fout.close();
    }

    /*
     * old -> new mapping xml table
     * if (!useIndex) {
     * Element rootElem = new Element("gribVarMap");
     * Document doc = new Document(rootElem);
     * rootElem.setAttribute("collection", dcm.getCollectionName());
     * 
     * for (String key : keys) {
     * Element param = new Element("param");
     * rootElem.addContent(param);
     * param.setAttribute("oldName", key);
     * List<String> newGrids = gridsAll.get(key);
     * Collections.sort(newGrids);
     * for (String newKey : newGrids)
     * param.addContent(new Element("newName").addContent(newKey));
     * }
     * 
     * FileOutputStream fout = new FileOutputStream("C:/tmp/gribVarMap.xml");
     * XMLOutputter fmt = new XMLOutputter(Format.getPrettyFormat());
     * fmt.output(doc, fout);
     * fout.close();
     * }
     */
  }

  private GridMatch altMatch(GridMatch want, Collection<GridMatch> test) {
    // look for scale factor errors in prob
    for (GridMatch gm : test) {
      if (gm.match != null)
        continue; // already matched
      if (gm.altMatch(want)) {
        // gm.altMatch(want); //debug
        return gm;
      }
    }

    // give up matching the prob
    for (GridMatch gm : test) {
      if (gm.match != null)
        continue; // already matched
      if (gm.altMatchNoProb(want)) {
        // gm.altMatchNoProb(want); // debug
        return gm;
      }
    }

    return null;
  }

  private static class VarName {
    String dataset;
    String oldVar;
    String newVar;
    String varId;

    private VarName(String dataset, String oldVar, String newVar, String varId) {
      this.dataset = dataset;
      this.oldVar = oldVar;
      this.newVar = newVar;
      this.varId = varId;
    }
  }

  private static class GridMatch implements Comparable<GridMatch> {
    GridDatatype grid;
    GridMatch match;
    boolean isNew;
    int[] param = new int[3];
    int level;
    boolean isLayer, isError;
    int interval = -1;
    int prob = -1;
    int ens = -1;
    int probLimit = Integer.MAX_VALUE;

    private GridMatch(GridDatatype grid, boolean aNew) {
      this.grid = grid;
      isNew = aNew;

      GridCoordSystem gcs = grid.getCoordinateSystem();
      CoordinateAxis1D zaxis = gcs.getVerticalAxis();
      if (zaxis != null)
        isLayer = zaxis.isInterval();

      if (isNew) {
        Attribute att = grid.findAttributeIgnoreCase("Grib2_Parameter");
        for (int i = 0; i < 3; i++)
          param[i] = att.getNumericValue(i).intValue();

        att = grid.findAttributeIgnoreCase("Grib2_Level_Type");
        level = att.getNumericValue().intValue();
        isError = grid.getName().contains("error");

        att = grid.findAttributeIgnoreCase("Grib2_Statistical_Interval_Type");
        if (att != null) {
          int intv = att.getNumericValue().intValue();
          if (intv != 255)
            interval = intv;
        }

        att = grid.findAttributeIgnoreCase("Grib2_Probability_Type");
        if (att != null)
          prob = att.getNumericValue().intValue();

        att = grid.findAttributeIgnoreCase("Grib2_Probability_Name"); // :Grib2_Probability_Name = "above_17.5";
        if (att != null) {
          String pname = att.getStringValue();
          int pos = pname.indexOf('_');
          pname = pname.substring(pos + 1);
          probLimit = (int) (1000.0 * Double.parseDouble(pname));
        }

        att = grid.findAttributeIgnoreCase("Grib2_Ensemble_Derived_Type");
        if (att != null)
          ens = att.getNumericValue().intValue();

      } else {
        Attribute att = grid.findAttributeIgnoreCase("GRIB_param_id");
        for (int i = 0; i < 3; i++)
          param[i] = att.getNumericValue(i + 1).intValue();

        att = grid.findAttributeIgnoreCase("GRIB_level_type");
        level = att.getNumericValue().intValue();
        isError = grid.getName().contains("error");

        att = grid.findAttributeIgnoreCase("GRIB_interval_stat_type");
        if (att != null) {
          String intName = att.getStringValue();
          interval = GribStatType.getStatTypeNumber(intName);
        }

        att = grid.findAttributeIgnoreCase("GRIB_probability_type");
        if (att != null)
          prob = att.getNumericValue().intValue();
        if (prob == 0) {
          att = grid.findAttributeIgnoreCase("GRIB_probability_lower_limit");
          if (att != null)
            probLimit = (int) (1000 * att.getNumericValue().doubleValue());
          // if (Math.abs(probLimit) > 100000) probLimit /= 1000; // wierd bug in 4.2
        } else if (prob == 1) {
          att = grid.findAttributeIgnoreCase("GRIB_probability_upper_limit"); // GRIB_probability_upper_limit = 12.89;
                                                                              // // double
          if (att != null)
            probLimit = (int) (1000 * att.getNumericValue().doubleValue());
          // if (Math.abs(probLimit) > 100000) probLimit /= 1000; // wierd bug in 4.2
        }

        att = grid.findAttributeIgnoreCase("GRIB_ensemble_derived_type");
        if (att != null)
          ens = att.getNumericValue().intValue();
      }
    }

    public boolean equals(Object o) {
      if (this == o)
        return true;
      if (o == null || getClass() != o.getClass())
        return false;

      GridMatch gridMatch = (GridMatch) o;

      if (ens != gridMatch.ens)
        return false;
      if (interval != gridMatch.interval)
        return false;
      if (isError != gridMatch.isError)
        return false;
      if (isLayer != gridMatch.isLayer)
        return false;
      if (level != gridMatch.level)
        return false;
      if (prob != gridMatch.prob)
        return false;
      if (probLimit != gridMatch.probLimit)
        return false;
      return Arrays.equals(param, gridMatch.param);

    }

    public boolean altMatch(GridMatch gridMatch) {
      if (!altMatchNoProb(gridMatch))
        return false;

      if (probLimit / 1000 == gridMatch.probLimit)
        return true;
      return probLimit == gridMatch.probLimit / 1000;

    }

    public boolean altMatchNoProb(Object o) {
      if (this == o)
        return true;
      if (o == null || getClass() != o.getClass())
        return false;

      GridMatch gridMatch = (GridMatch) o;

      if (!Arrays.equals(param, gridMatch.param))
        return false;
      if (ens != gridMatch.ens)
        return false;
      if (interval != gridMatch.interval)
        return false;
      if (isError != gridMatch.isError)
        return false;
      if (isLayer != gridMatch.isLayer)
        return false;
      if (level != gridMatch.level)
        return false;
      return prob == gridMatch.prob;

    }


    public int hashCode() {
      int result = 1;
      result = 31 * result + level;
      result = 31 * result + param[0];
      result = 31 * result + (isLayer ? 1 : 0);
      result = 31 * result + param[1];
      result = 31 * result + (isError ? 1 : 0);
      if (interval >= 0)
        result = 31 * result + interval;
      if (prob >= 0)
        result = 31 * result + prob;
      result = 31 * result + param[2];
      if (ens >= 0)
        result = 31 * result + ens;
      if (probLimit != Integer.MAX_VALUE)
        result = 31 * result + probLimit;
      return result;
    }

    @Override
    public int compareTo(GridMatch o) {
      return grid.compareTo(o.grid);
    }

    String show() {
      Formatter f = new Formatter();
      for (int i = 0; i < 3; i++)
        f.format("%d-", param[i]);
      f.format("%d", level);
      if (isLayer)
        f.format("_layer");
      if (interval >= 0)
        f.format("_intv%d", interval);
      if (prob >= 0)
        f.format("_prob%d_%d", prob, probLimit);
      if (ens >= 0)
        f.format("_ens%d", ens);
      if (isError)
        f.format("_error");
      return f.toString();
    }
  }

  private Map<Integer, GridMatch> getGridsNew(MFile ff, Formatter f) throws IOException {
    Map<Integer, GridMatch> grids = new HashMap<>(100);
    try (GridDataset ncfile = GridDataset.open(ff.getPath())) {
      for (GridDatatype dt : ncfile.getGrids()) {
        GridMatch gm = new GridMatch(dt, true);
        GridMatch dup = grids.get(gm.hashCode());
        if (dup != null)
          f.format(" DUP NEW (%d == %d) = %s (%s) and DUP %s (%s)%n", gm.hashCode(), dup.hashCode(),
              gm.grid.getFullName(), gm.show(), dup.grid.getFullName(), dup.show());
        else
          grids.put(gm.hashCode(), gm);
      }
    }
    return grids;
  }

  private Map<Integer, GridMatch> getGridsOld(MFile ff, Formatter f) {
    Map<Integer, GridMatch> grids = new HashMap<>(100);
    try (NetcdfFile ncfile = NetcdfFiles.open(ff.getPath(), "ucar.nc2.iosp.grib.GribServiceProvider", -1, null, null)) {
      NetcdfDataset ncd = NetcdfDatasets.enhance(ncfile, NetcdfDataset.getDefaultEnhanceMode(), null);
      GridDataset grid = new GridDataset(ncd);
      for (GridDatatype dt : grid.getGrids()) {
        GridMatch gm = new GridMatch(dt, false);
        GridMatch dup = grids.get(gm.hashCode());
        if (dup != null)
          f.format(" DUP OLD (%d == %d) = %s (%s) and DUP %s (%s)%n", gm.hashCode(), dup.hashCode(),
              gm.grid.getFullName(), gm.show(), dup.grid.getFullName(), dup.show());
        else
          grids.put(gm.hashCode(), gm);
      }
    } catch (Throwable t) {
      t.printStackTrace();
    }
    return grids;
  }

}
