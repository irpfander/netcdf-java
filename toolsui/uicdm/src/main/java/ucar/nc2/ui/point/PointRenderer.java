/*
 * Copyright (c) 1998 - 2009. University Corporation for Atmospheric Research/Unidata
 * Portions of this software were developed by the Unidata Program at the
 * University Corporation for Atmospheric Research.
 *
 * Access and use of this software shall impose the following obligations
 * and understandings on the user. The user is granted the right, without
 * any fee or cost, to use, copy, modify, alter, enhance and distribute
 * this software, and any derivative works thereof, and its supporting
 * documentation for any purpose whatsoever, provided that this entire
 * notice appears in all copies of the software, derivative works and
 * supporting documentation. Further, UCAR requests that the user credit
 * UCAR/Unidata in any publications that result from the use of this
 * software or in any product that includes this software. The names UCAR
 * and/or Unidata, however, may not be used in any advertising or publicity
 * to endorse or promote any products or commercial entity unless specific
 * written permission is obtained from UCAR/Unidata. The user also
 * understands that UCAR/Unidata is not obligated to provide the user with
 * any support, consulting, training or assistance of any kind with regard
 * to the use, operation and performance of this software nor to provide
 * the user with any updates, revisions, new versions or "bug fixes."
 *
 * THIS SOFTWARE IS PROVIDED BY UCAR/UNIDATA "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL UCAR/UNIDATA BE LIABLE FOR ANY SPECIAL,
 * INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING
 * FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT,
 * NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION
 * WITH THE ACCESS, USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package ucar.nc2.ui.point;

import ucar.nc2.ui.util.Renderer;
import ucar.unidata.geoloc.*;
import ucar.nc2.ft.PointFeature;
import java.util.ArrayList;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import ucar.ui.widget.FontUtil;

public class PointRenderer implements Renderer {
  private java.util.List<ObservationUI> obsUIlist = new ArrayList<>(); // ObservationUI objects
  private Projection project; // display projection

  // drawing parameters
  private Color color = Color.black;
  private final Color selectedColor = Color.magenta;
  private final int circleRadius = 3;
  private final Rectangle2D circleBB =
      new Rectangle2D.Double(-circleRadius, -circleRadius, 2 * circleRadius, 2 * circleRadius);
  private final FontUtil.StandardFont textFont;
  private boolean drawConnectingLine;

  private ObservationUI selected;

  // misc state
  private boolean declutter = true;
  private boolean posWasCalc;

  /** constructor */
  public PointRenderer() {
    textFont = FontUtil.getStandardFont(10);
  }

  public void incrFontSize() {
    textFont.incrFontSize();
  }

  public void decrFontSize() {
    textFont.decrFontSize();
  }

  public void setColor(java.awt.Color color) {
    this.color = color;
  }

  public java.awt.Color getColor() {
    return color;
  }

  public LatLonRect getPreferredArea() {
    return null;
  }

  public void setPointFeatures(java.util.List<PointFeature> obs) {
    obsUIlist = new ArrayList<>(obs.size());
    for (PointFeature ob : obs) {
      ObservationUI sui = new ObservationUI(ob); // wrap in a StationUI
      obsUIlist.add(sui); // wrap in a StationUI
    }
    posWasCalc = false;
    calcWorldPos();
    selected = null;
  }

  public void setDrawConnectingLine(boolean drawConnectingLine) {
    this.drawConnectingLine = drawConnectingLine;
  }

  public void setSelected(PointFeature obs) {
    selected = null;
    for (ObservationUI observationUI : obsUIlist) {
      if (testPointObsDatatype(observationUI.obs, obs)) {
        selected = observationUI;
        break;
      }
    }
  }

  private boolean testPointObsDatatype(PointFeature obs1, PointFeature obs2) {
    if (obs1.getObservationTime() != obs2.getObservationTime())
      return false;
    ucar.unidata.geoloc.EarthLocation loc1 = obs1.getLocation();
    ucar.unidata.geoloc.EarthLocation loc2 = obs2.getLocation();
    if (loc1.getLatitude() != loc2.getLatitude())
      return false;
    return loc1.getLongitude() == loc2.getLongitude();
  }

  public void setDeclutter(boolean declut) {
    declutter = declut;
  }

  public boolean getDeclutter() {
    return declutter;
  }

  public void setProjection(Projection project) {
    this.project = project;
    calcWorldPos();
  }

  private void calcWorldPos() {
    if (project == null)
      return;
    for (ObservationUI observationUI : obsUIlist) {
      observationUI.worldPos = project.latLonToProj(observationUI.latlonPos);
    }
    posWasCalc = true;
  }

  public void draw(java.awt.Graphics2D g, java.awt.geom.AffineTransform normal2Device) {
    if ((project == null) || !posWasCalc)
      return;

    // use world coordinates for position, but draw in screen coordinates
    // so that the symbols stay the same size
    AffineTransform world2Device = g.getTransform();
    g.setTransform(normal2Device); // identity transform for screen coords

    // transform World to Normal coords:
    // world2Normal = pixelAT-1 * world2Device
    // cache for pick closest
    AffineTransform world2Normal;
    try {
      world2Normal = normal2Device.createInverse();
      world2Normal.concatenate(world2Device);
    } catch (java.awt.geom.NoninvertibleTransformException e) {
      System.out.println(" RendSurfObs: NoninvertibleTransformException on " + normal2Device);
      return;
    }

    // we want aliasing; but save previous state to restore at end
    Object saveHint = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
    // g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
    // RenderingHints.VALUE_ANTIALIAS_ON);
    g.setStroke(new java.awt.BasicStroke(1.0f));

    g.setFont(textFont.getFont());
    g.setColor(color);

    int count = 0;
    int npts = obsUIlist.size();
    GeneralPath path = new GeneralPath(GeneralPath.WIND_EVEN_ODD, npts);
    for (ObservationUI observationUI : obsUIlist) {
      ObservationUI s = observationUI;
      s.calcPos(world2Normal);
      s.draw(g);

      if (Double.isNaN(s.screenPos.getX())) {
        System.out.println("screenPos=" + s.screenPos + " world = " + s.worldPos);
        continue;
      }

      if (count == 0) {
        path.moveTo((float) s.screenPos.getX(), (float) s.screenPos.getY());
      } else {
        path.lineTo((float) s.screenPos.getX(), (float) s.screenPos.getY());
      }
      count++;
    }

    g.setColor(color);
    if (drawConnectingLine)
      g.draw(path);

    // draw selected
    if (selected != null)
      selected.draw(g);

    // restore
    g.setTransform(world2Device);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, saveHint);
  }


  public class ObservationUI {
    private PointFeature obs;

    private LatLonPoint latlonPos; // latlon pos
    private ProjectionPoint worldPos = ProjectionPoint.create(0, 0);// world pos
    private Point2D.Double screenPos = new Point2D.Double(); // normalized screen pos

    private Rectangle2D bb; // bounding box, in normalized screen coords, loc = 0, 0
    private Rectangle2D bbPos = new Rectangle2D.Double(); // bounding box, translated to current drawing pos
    // private boolean selected = false;

    ObservationUI(PointFeature obs) {
      this.obs = obs;
      latlonPos = LatLonPoint.create(obs.getLocation().getLatitude(), obs.getLocation().getLongitude());

      // text bb
      Dimension t = textFont.getBoundingBox("O"); // LOOK temp
      bb = new Rectangle2D.Double(-circleRadius, -circleRadius - t.getHeight(), t.getWidth(), t.getHeight());
      // add circle bb
      bb.add(circleBB);
    }

    public PointFeature getObservation() {
      return obs;
    }

    public LatLonPoint getLatLon() {
      return latlonPos;
    }

    public ProjectionPoint getLocation() {
      return worldPos;
    }

    // public boolean isSelected() { return selected; }
    // public void setIsSelected(boolean selected) { this.selected = selected; }
    public Rectangle2D getBB() {
      return bbPos;
    }

    boolean contains(Point p) {
      return bbPos.contains(p);
    }

    void calcPos(AffineTransform w2n) {
      w2n.transform(new Point2D.Double(worldPos.getX(), worldPos.getY()), screenPos); // work in normalized coordinate
                                                                                      // space
      bbPos.setRect(screenPos.getX() + bb.getX(), screenPos.getY() + bb.getY(), bb.getWidth(), bb.getHeight());
    }

    void draw(Graphics2D g) {
      if (this == selected) {
        g.setColor(selectedColor);
        fillCircle(g, screenPos);
        g.setColor(color);
      } else
        drawCircle(g, screenPos);
      // drawText(g, screenPos, id);
    }

    /**
     * draw the symbol at the specified location
     * 
     * @param g Graphics2D object
     * @param loc the reference point (center of the CloudSymbol icon offset from here)
     */
    private void drawCircle(Graphics2D g, Point2D loc) {
      int x = (int) (loc.getX() - circleRadius);
      int y = (int) (loc.getY() - circleRadius);
      g.drawOval(x, y, 2 * circleRadius, 2 * circleRadius);
    }

    private void fillCircle(Graphics2D g, Point2D loc) {
      int x = (int) (loc.getX() - circleRadius);
      int y = (int) (loc.getY() - circleRadius);
      g.fillOval(x, y, 2 * circleRadius, 2 * circleRadius);
    }

  } // end inner class ObservationUI

}


