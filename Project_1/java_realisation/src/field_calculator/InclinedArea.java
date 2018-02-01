package field_calculator;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javafx.scene.image.ImageView;
import org.apache.commons.math3.complex.Complex;
import org.jzy3d.colors.Color;
import org.jzy3d.colors.ColorMapper;
import org.jzy3d.colors.colormaps.ColorMapRainbowNoBorder;
import org.jzy3d.maths.Coord3d;
import org.jzy3d.plot3d.primitives.Point;
import org.jzy3d.plot3d.primitives.Polygon;
import org.jzy3d.plot3d.primitives.Shape;

public class InclinedArea extends Area implements Serializable {
	/*
	Properties from super class Area:

	public double xSize;
	public double ySize;
	public double xStep;
	public double yStep;
	public double[][] gridX;
	public double[][] gridY;
	public HashMap<Integer, Source> sources = new HashMap<Integer, Source>();
	private double[][][] sumFieldAbs;
	private double[][][] sumFieldPhase;
	*/

    public InclinedArea(double xSize_, double ySize_, double xStep_, double yStep_, double hMax) {
        xSize = xSize_;
        ySize = ySize_;
        xStep = xStep_;
        yStep = yStep_;
        zSize = hMax;

        double numXPoints = xSize / xStep + 1;
        if ((numXPoints - Math.floor(numXPoints)) != 0) {
            numXPoints += 1;
            xStep = xSize / numXPoints;
        }

        double numYPoints = ySize / yStep + 1;
        if ((numYPoints - Math.floor(numYPoints)) != 0) {
            numYPoints += 1;
            yStep = ySize / numYPoints;
        }

        double[] xPoints = SomeMath.linspace(0, xSize, (int) numXPoints);
        double[] yPoints = SomeMath.linspace(0, ySize, (int) numYPoints);

        ArrayList<double[][]> grids = SomeMath.meshgrid(xPoints, yPoints);
        gridX = grids.get(0);
        gridY = grids.get(1);
        double kIncline = zSize / xSize;
        gridZ = Matrix.dotMultiply(kIncline, gridX);
    }



    public void calcSummPreasure(int freqIdx) {
        if (sumFieldAbs == null) {
            sumFieldAbs = new double[32][gridX.length][gridX[0].length];
            sumFieldPhase = new double[32][gridX.length][gridX[0].length];
        }


        Complex[][] p = new Complex [gridX.length][gridX[0].length];
        for(int i = 0; i < p.length; i++) {
            for (int j = 0; j < p[0].length; j++) {
                p[i][j] = new Complex(0,0);
            }
        }

        for (Integer key: sources.keySet()) {

            Source curSource = sources.get(key);

            double[][] ro = Matrix.dotMultiply(P0, Matrix.pow(10, Matrix.dotMultiply(0.05, curSource.preasureAbs[freqIdx])));
            double[][] phi = Matrix.dotMultiply(-1, curSource.preasurePhase[freqIdx]);

            for (int i = 0; i < gridX.length; i++) {
                for (int j = 0; j < gridX[0].length; j++) {
                    double Re = ro[i][j] * Math.cos(phi[i][j]);
                    double Im = ro[i][j] * Math.sin(phi[i][j]);
                    Complex pCur = new Complex(Re, Im);
                    p[i][j] = p[i][j].add(pCur);
                }
            }
        }

        double[][] absP = new double[gridX.length][gridX[0].length];
        for (int i = 0; i < gridX.length; i++) {
            for (int j = 0; j < gridX[0].length; j++) {
                absP[i][j] = p[i][j].abs();
            }
        }

        double[][] angP = new double[gridX.length][gridX[0].length];
        for (int i = 0; i < gridX.length; i++) {
            for (int j = 0; j < gridX[0].length; j++) {
                angP[i][j] = p[i][j].getArgument();
            }
        }

        double oneDivP0 = 1/P0;
        sumFieldAbs[freqIdx] = Matrix.dotMultiply(20, Matrix.dotLog10(Matrix.dotMultiply(oneDivP0,absP)));
        sumFieldPhase[freqIdx] = angP;
    }

    public ImageView plotSurface() {
        Shape surface = buildSurface();
        return Plotter.plotRectangularSurface(surface);
    }

    public ImageView plotSources() {
        Shape surface = buildSurface();
        ImageView imageView = Plotter.plotAllSourcesRectArea(this, surface);
        return imageView;
    }

    public ImageView plotLightedSource(int sourceNum) {
        Shape surface = buildSurface();
        ImageView imageView = Plotter.plotLightedSourceRectArea(this, surface, sourceNum);
        return imageView;
    }

    private Shape buildSurface() {
        return Plotter.buildRectInclinedSurface(gridX, gridY, gridZ);
    }

    public ImageView plotSingleSourceField(int freqIdx, int sourceNum) {
        Shape surface = buildPreasureFieldShape(freqIdx, true, sourceNum);
        ImageView imageView = Plotter.plotField(surface);
        return imageView;
    }

    public ImageView plotSummaryField(int freqIdx) {
        Shape surface = buildPreasureFieldShape(freqIdx, false, -1);
        ImageView imageView = Plotter.plotField(surface);
        return imageView;
    }

    private Shape buildPreasureFieldShape(int freqIdx, boolean isSingle, int sourceNum) {
        double[][] x = gridX;
        double[][] y = gridY;
        double[][] pressure;
        if(isSingle) {
            pressure = sources.get(sourceNum).preasureAbs[freqIdx];
        } else {
            pressure = sumFieldAbs[freqIdx];
        }

        // Create the 3d object
        List<Polygon> polygons = new ArrayList<>();
        for (int i = 0; i < (x.length - 1); i++) {
            for (int j = 0; j < (x[0].length - 1); j++) {
                Polygon polygon = new Polygon();
                polygon.add(new Point( new Coord3d(x[i][j], y[i][j], pressure[i][j]) ));
                polygon.add(new Point( new Coord3d(x[i][j+1], y[i][j+1], pressure[i][j+1])));
                polygon.add(new Point( new Coord3d(x[i+1][j+1], y[i+1][j+1], pressure[i+1][j+1])));
                polygon.add(new Point( new Coord3d(x[i+1][j], y[i+1][j], pressure[i+1][j])));
                polygons.add(polygon);
            }
        }

        // Jzy3d
        Shape surface = new Shape(polygons);
        surface.setColorMapper(new ColorMapper(new ColorMapRainbowNoBorder(), surface.getBounds().getZmin(), surface.getBounds().getZmax(), new Color(1,1,1,1f)));
        surface.setWireframeDisplayed(false);
        surface.setWireframeColor(Color.GRAY);

        return surface;
    }


}

