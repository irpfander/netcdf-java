/*
 * Copyright 1998-2014 University Corporation for Atmospheric Research/Unidata
 *
 *   Portions of this software were developed by the Unidata Program at the
 *   University Corporation for Atmospheric Research.
 *
 *   Access and use of this software shall impose the following obligations
 *   and understandings on the user. The user is granted the right, without
 *   any fee or cost, to use, copy, modify, alter, enhance and distribute
 *   this software, and any derivative works thereof, and its supporting
 *   documentation for any purpose whatsoever, provided that this entire
 *   notice appears in all copies of the software, derivative works and
 *   supporting documentation.  Further, UCAR requests that the user credit
 *   UCAR/Unidata in any publications that result from the use of this
 *   software or in any product that includes this software. The names UCAR
 *   and/or Unidata, however, may not be used in any advertising or publicity
 *   to endorse or promote any products or commercial entity unless specific
 *   written permission is obtained from UCAR/Unidata. The user also
 *   understands that UCAR/Unidata is not obligated to provide the user with
 *   any support, consulting, training or assistance of any kind with regard
 *   to the use, operation and performance of this software nor to provide
 *   the user with any updates, revisions, new versions or "bug fixes."
 *
 *   THIS SOFTWARE IS PROVIDED BY UCAR/UNIDATA "AS IS" AND ANY EXPRESS OR
 *   IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *   WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *   DISCLAIMED. IN NO EVENT SHALL UCAR/UNIDATA BE LIABLE FOR ANY SPECIAL,
 *   INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING
 *   FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT,
 *   NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION
 *   WITH THE ACCESS, USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package thredds.server.ncss.view.gridaspoint.gridaspoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import thredds.server.ncss.controller.NcssRequestUtils;
import thredds.util.ContentType;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.Attribute;
import ucar.nc2.constants.CDM;
import ucar.nc2.ft2.coverage.grid.GridCoordAxis;
import ucar.nc2.ft2.coverage.grid.GridCoordSys;
import ucar.nc2.ft2.coverage.grid.GridCoverage;
import ucar.nc2.ft2.coverage.grid.GridCoverageDataset;
import ucar.nc2.time.CalendarDate;
import ucar.nc2.time.CalendarDateRange;
import ucar.unidata.geoloc.LatLonPoint;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

class XMLGridAsPointWriter extends GridAsPointWriter {
  static private Logger log = LoggerFactory.getLogger(XMLGridAsPointWriter.class);

  private GridCoverageDataset gds;
  private Map<String, List<String>> allVars;
  private Map<String, GridAsPointCoverageDataset> gridAsPointDatasets =new HashMap<>();
  private XMLStreamWriter xmlStreamWriter;
  private HttpHeaders httpHeaders = new HttpHeaders();

  XMLGridAsPointWriter(GridCoverageDataset gds, OutputStream os) {
    this.gds = gds;
    xmlStreamWriter = createXMLStreamWriter(os);
  }

  //public boolean header(Map<String, List<String>> vars, GridDataset gridDataset, List<CalendarDate> wDates, DateUnit dateUnit,LatLonPoint point, Double vertCoord) {
  @Override
  public boolean header(Map<String, List<String>> groupedVars, CalendarDateRange calendarDateRange,
                        List<Attribute> timeDimAtts, LatLonPoint point, Double vertCoord) {
    boolean headerWritten = false;
    try {
      xmlStreamWriter.writeStartDocument("UTF-8", "1.0");
      xmlStreamWriter.writeStartElement("grid");
      xmlStreamWriter.writeAttribute("dataset", gds.getName());
      headerWritten = true;
    } catch (XMLStreamException xse) {
      log.error("Error writting xml header", xse);
    }

    return headerWritten;
  }

  @Override
  public boolean write(Map<String, List<String>> groupVarsByVertLevels, CalendarDateRange calendarDateRange, LatLonPoint point, Double vertCoord) throws InvalidRangeException, IOException {

    //loop over wDates
    CalendarDate date;
    boolean pointRead = true;
    List<String> keysAsList = new ArrayList<>(groupVarsByVertLevels.keySet());
    return writeOneDate(keysAsList, calendarDateRange.getStart(), point, vertCoord);      // LOOK LOOK
  }

  ///////////////////////////////////////////////////////////////
  // write one group of variable that all have the same axis

  /* private boolean writeNoTimeAxis(List<String> groupsKeys, LatLonPoint point, Double targetLevel) throws IOException {

    boolean allDone = true;

    //loop over variable groups
    for (String key : groupsKeys) {
      // get wanted vertCoords for group (all if vertCoord==null just one otherwise)
      List<String> varsGroup = allVars.get(key);
      GridAsPointCoverageDataset gap = gridAsPointDatasets.get(key);
      GridCoordSys gcs = gds.findCoordSys(varsGroup.get(0));
      GridCoordAxis verticalAxisForGroup = gds.getZAxis(gcs);
      if (verticalAxisForGroup == null) {
        //Read and write vars--> time, point
        allDone = allDone && writeNoTimeNoVert(varsGroup, gap, point);

      } else {
        //read and write time, verCoord for each variable in group
        if (targetLevel != null) {
          Double vertCoord = NcssRequestUtils.getTargetLevelForVertCoord(verticalAxisForGroup, targetLevel);
          allDone = writeVertNoTime(varsGroup, gap, point, vertCoord, verticalAxisForGroup.getUnits());

        } else { //All levels
          for (Double vertCoord : verticalAxisForGroup.readValues()) {
            /////Fix axis!!!!
            if (verticalAxisForGroup.getNvalues() == 1)
              vertCoord = NcssRequestUtils.getTargetLevelForVertCoord(verticalAxisForGroup, vertCoord); // LOOK WTF ??

            allDone = allDone && writeVertNoTime(varsGroup, gap, point, vertCoord, verticalAxisForGroup.getUnits());

          }
        }

      }

    }
    return allDone;

  }  */

  private boolean writeOneDate(List<String> groupsKeys, CalendarDate date, LatLonPoint point, Double targetLevel) throws InvalidRangeException, IOException {

    boolean allDone = true;

    //loop over variable groups
    for (String key : groupsKeys) {
      //get wanted vertCoords for group (all if vertCoord==null just one otherwise)
      List<String> varsGroup = allVars.get(key);
      //GridAsPointCoverageDataset gap = NcssRequestUtils.buildGridAsPointCoverageDataset(gridDataset,	varsGroup);
      GridAsPointCoverageDataset gap = gridAsPointDatasets.get(key);
      GridCoverage grid = gds.findCoverage(varsGroup.get(0));
      GridCoordSys gcs = gds.findCoordSys(varsGroup.get(0));
      GridCoordAxis verticalAxisForGroup = gds.getZAxis(gcs);
      if (verticalAxisForGroup == null) {
        //Read and write vars--> time, point
        // allDone = allDone && writeTimeNoVert(varsGroup, gap, date, point);
      } else {
        //read and write time, verCoord for each variable in group
        if (targetLevel != null) {
          Double vertCoord = NcssRequestUtils.getTargetLevelForVertCoord(verticalAxisForGroup, targetLevel);
          // allDone = writeTimeAndVert(varsGroup, gap, date, point, vertCoord, verticalAxisForGroup.getUnits());
        } else {//All levels
          for (Double vertCoord : verticalAxisForGroup.readValues()) {
            /////Fix axis!!!!
            if (verticalAxisForGroup.getNvalues() == 1)
              vertCoord = NcssRequestUtils.getTargetLevelForVertCoord(verticalAxisForGroup, vertCoord);  // LOOK WTF ?

            // allDone = allDone && writeTimeAndVert(varsGroup, gap, date, point, vertCoord, verticalAxisForGroup.getUnits());

          }
        }

      }

    }
    return allDone;
  }


  ///////////////////////////////////////////////
  // GridAsPointCoverageDataset below

  /* private boolean writeTimeAndVert(List<String> vars, GridAsPointCoverageDataset gap, CalendarDate date, LatLonPoint point, Double targetLevel, String zUnits) throws InvalidRangeException {

    Iterator<String> itVars = vars.iterator();
    boolean pointDone = false;
    try {
      xmlStreamWriter.writeStartElement("point");
      Map<String, String> attributes = new HashMap<>();
      attributes.put("name", "date");
      writeDataTag(xmlStreamWriter, attributes, date.toString());
      attributes.clear();
      int contVars = 0;
      while (itVars.hasNext()) {
        String varName = itVars.next();
        GridCoverage grid = gds.findCoverage(varName);
        //Handling the ensemble dimension...
        CoordinateAxis1D ensembleAxis = null; // grid.getCoordinateSystem().getEnsembleAxis();
        boolean hasEnsembleDim = false;
        double[] ensCoords = new double[]{-1};
        if (ensembleAxis != null) {
          ensCoords = ensembleAxis.getCoordValues();
          hasEnsembleDim = true;
        }


        double actualLevel = NcssRequestUtils.getActualVertLevel(gds, grid, date, point, targetLevel);

        for (double ensCoord : ensCoords) {

          if (gap.hasTime(grid, date) && gap.hasVert(grid, targetLevel)) {
            GridAsPointCoverageDataset.Point p = gap.readData(grid, date, ensCoord, targetLevel, point.getLatitude(), point.getLongitude());

            if (contVars == 0) {
              //writeCoordinates(xmlStreamWriter, Double.valueOf(p.lat), Double.valueOf(p.lon));
              writeCoordinates(xmlStreamWriter, point.getLatitude(), point.getLongitude());
              attributes.put("name", "vertCoord");
              attributes.put("units", zUnits);
              writeDataTag(xmlStreamWriter, attributes, Double.valueOf(p.z).toString());
              attributes.clear();

              if (Double.compare(actualLevel, -9999.9) != 0) {  // LOOK WTF ??

                attributes.put("name", "vertCoord");
                attributes.put("units", grid.getCoordinateSystem().getVerticalTransform().getUnitString());
                writeDataTag(xmlStreamWriter, attributes, Double.valueOf(actualLevel).toString());
                attributes.clear();

              }

            }
            attributes.put("name", varName);
            attributes.put("units", grid.getUnits());
            if (hasEnsembleDim)
              //attributes.put("ensMember", Integer.valueOf((int)ensCoord).toString() );
              attributes.put("ensMember", Double.valueOf(p.ens).toString());

            writeDataTag(xmlStreamWriter, attributes, Double.valueOf(p.dataValue).toString());
            attributes.clear();

          } else {
            // write missingvalues!!!
            if (contVars == 0) {
              writeCoordinates(xmlStreamWriter, point.getLatitude(), point.getLongitude());
            }
            attributes.put("name", varName);
            attributes.put("units", grid.getUnits());
            writeDataTag(xmlStreamWriter, attributes, Double.valueOf(gap.getMissingValue(grid)).toString());
            attributes.clear();
          }
          contVars++;
        }
      }
      xmlStreamWriter.writeEndElement(); //Closes point
      pointDone = true;
    } catch (XMLStreamException xse) {
      log.error("Error writting tag point", xse);
    } catch (IOException ioe) {
      log.error("Error reading point data", ioe);
    }

    return pointDone;
  }

  /**
   * Write method when the grid has no time axis but has vertical axis
   *
  private boolean writeVertNoTime(List<String> vars, GridAsPointCoverageDataset gap, LatLonPoint point, Double targetLevel, String zUnits) {

    Iterator<String> itVars = vars.iterator();
    boolean pointDone = false;
    try {
      xmlStreamWriter.writeStartElement("point");
      Map<String, String> attributes = new HashMap<>();
      //attributes.put("name", "date");
      //writeDataTag( xmlStreamWriter, attributes, date.toString() );
      //attributes.clear();
      int contVars = 0;
      while (itVars.hasNext()) {
        String varName = itVars.next();
        GridCoverage grid = gds.findCoverage(varName);

        if (gap.hasVert(grid, targetLevel)) {
          GridAsPointCoverageDataset.Point p = gap.readData(grid, null, targetLevel, point.getLatitude(), point.getLongitude());
          if (contVars == 0) {
            //writeCoordinates(xmlStreamWriter, Double.valueOf(p.lat), Double.valueOf(p.lon));
            writeCoordinates(xmlStreamWriter, point.getLatitude(), point.getLongitude());
            attributes.put("name", "vertCoord");
            attributes.put("units", zUnits);
            writeDataTag(xmlStreamWriter, attributes, Double.valueOf(p.z).toString());
            attributes.clear();
          }
          attributes.put("name", varName);
          attributes.put("units", grid.getUnits());
          writeDataTag(xmlStreamWriter, attributes, Double.valueOf(p.dataValue).toString());
          attributes.clear();

        } else {
          // write missingvalues!!!
          if (contVars == 0) {
            writeCoordinates(xmlStreamWriter, point.getLatitude(), point.getLongitude());
          }
          attributes.put("name", varName);
          attributes.put("units", grid.getUnits());
          writeDataTag(xmlStreamWriter, attributes, Double.valueOf(gap.getMissingValue(grid)).toString());
          attributes.clear();
        }
        contVars++;
      }
      xmlStreamWriter.writeEndElement(); //Closes point
      pointDone = true;
    } catch (XMLStreamException xse) {
      log.error("Error writting tag point", xse);
    } catch (IOException ioe) {
      log.error("Error reading point data", ioe);
    }

    return pointDone;
  }

  /**
   * Write method when the grid has no time axis and no vertical axis
   *
  private boolean writeNoTimeNoVert(List<String> vars, GridAsPointCoverageDataset gap, LatLonPoint point) {

    Iterator<String> itVars = vars.iterator();
    boolean pointDone = false;
    try {
      xmlStreamWriter.writeStartElement("point");
      Map<String, String> attributes = new HashMap<>();
      attributes.clear();
      int contVars = 0;
      while (itVars.hasNext()) {
        String varName = itVars.next();
        GridCoverage grid = gds.findCoverage(varName);

        GridAsPointCoverageDataset.Point p = gap.readData(grid, null, point.getLatitude(), point.getLongitude());
        if (contVars == 0) {
          //writeCoordinates(xmlStreamWriter, Double.valueOf(p.lat), Double.valueOf(p.lon));
          writeCoordinates(xmlStreamWriter, point.getLatitude(), point.getLongitude());
          attributes.clear();
        }
        attributes.put("name", varName);
        attributes.put("units", grid.getUnits());
        writeDataTag(xmlStreamWriter, attributes, Double.valueOf(p.dataValue).toString());
        attributes.clear();

        contVars++;
      }
      xmlStreamWriter.writeEndElement(); //Closes point
      pointDone = true;
    } catch (XMLStreamException xse) {
      log.error("Error writting tag point", xse);
    } catch (IOException ioe) {
      log.error("Error reading point data", ioe);
    }

    return pointDone;

  }

  /**
   * Write method for grids with time axis but not vertical level
   *
  private boolean writeTimeNoVert(List<String> vars, GridAsPointCoverageDataset gap, CalendarDate date, LatLonPoint point) {

    Iterator<String> itVars = vars.iterator();
    boolean pointDone = false;
    try {
      xmlStreamWriter.writeStartElement("point");
      Map<String, String> attributes = new HashMap<>();
      attributes.put("name", "date");
      writeDataTag(xmlStreamWriter, attributes, date.toString());
      attributes.clear();
      int contVars = 0;
      while (itVars.hasNext()) {
        String varName = itVars.next();
        GridCoverage grid = gds.findCoverage(varName);

        //Handling the ensemble dimension...
        CoordinateAxis1D ensembleAxis = grid.getCoordinateSystem().getEnsembleAxis();
        boolean hasEnsembleDim = false;
        double[] ensCoords = new double[]{-1};
        if (ensembleAxis != null) {
          ensCoords = ensembleAxis.getCoordValues();
          hasEnsembleDim = true;
        }

        for (double ensCoord : ensCoords) {

          if (gap.hasTime(grid, date)) {
            GridAsPointCoverageDataset.Point p = gap.readData(grid, date, ensCoord, -1, point.getLatitude(), point.getLongitude());
            if (contVars == 0) {
              writeCoordinates(xmlStreamWriter, point.getLatitude(), point.getLongitude());
              attributes.clear();
            }
            attributes.put("name", varName);
            attributes.put("units", grid.getUnits());
            if (hasEnsembleDim)
              attributes.put("ensMember", Double.valueOf(p.ens).toString());

            writeDataTag(xmlStreamWriter, attributes, Double.valueOf(p.dataValue).toString());

            attributes.clear();

          } else {
            // write missingvalues!!!
            if (contVars == 0) {
              writeCoordinates(xmlStreamWriter, point.getLatitude(), point.getLongitude());
            }
            attributes.put("name", varName);
            attributes.put("units", grid.getUnits());
            writeDataTag(xmlStreamWriter, attributes, Double.valueOf(gap.getMissingValue(grid)).toString());
            attributes.clear();
          }
          contVars++;
        }
      }
      xmlStreamWriter.writeEndElement(); //Closes point
      pointDone = true;
    } catch (XMLStreamException xse) {
      log.error("Error writting tag point", xse);
    } catch (IOException ioe) {
      log.error("Error reading point data", ioe);
    }

    return pointDone;
  }  */


  @Override
  public boolean trailer() {
    boolean endDocument = false;
    try {
      xmlStreamWriter.writeEndElement(); // Closes tag grid
      xmlStreamWriter.writeEndDocument();
      endDocument = true;
    } catch (XMLStreamException xse) {
      log.error("Error writing end document", xse);
    }

    return endDocument;
  }

  @Override
  public HttpHeaders getResponseHeaders() {
    return httpHeaders;
  }

  private XMLStreamWriter createXMLStreamWriter(OutputStream os) {
    XMLOutputFactory outputFactory = XMLOutputFactory.newFactory();
    XMLStreamWriter writer = null;

    try {
      writer = outputFactory.createXMLStreamWriter(os, "UTF-8");
    } catch (XMLStreamException xse) {
      log.error(xse.getMessage());
    }

    return writer;
  }

  @Override
  public void setHTTPHeaders(String pathInfo, boolean isStream) {
    //Set the response headers...
    if (!isStream) {
      httpHeaders.set("Content-Location", pathInfo);
      String fileName = NcssRequestUtils.getFileNameForResponse(pathInfo, ".xml");
      httpHeaders.set("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
    }
    httpHeaders.set(ContentType.HEADER, ContentType.xml.getContentHeader());
    //httpHeaders.setContentType(MediaType.APPLICATION_XML);
  }

  private void writeDataTag(XMLStreamWriter writer, Map<String, String> attributes, String content) throws XMLStreamException {

    writer.writeStartElement("data");
    for (Map.Entry<String, String> entry : attributes.entrySet()) {
      writer.writeAttribute(entry.getKey(), entry.getValue());
    }

    writer.writeCharacters(content);
    writer.writeEndElement();
  }

  private void writeCoordinates(XMLStreamWriter writer, Double lat, Double lon) throws XMLStreamException {

    Map<String, String> attributes = new HashMap<>();
    // tag data for lat
    attributes.put("name", "lat");
    attributes.put("units", CDM.LAT_UNITS);
    writeDataTag(writer, attributes, lat.toString());
    attributes.clear();
    // tag data for lon
    attributes.put("name", "lon");
    attributes.put("units", CDM.LON_UNITS);
    writeDataTag(writer, attributes, lon.toString());
    attributes.clear();
  }

}