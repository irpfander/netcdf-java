package examples.runtime;

import ucar.nc2.constants.FeatureType;
import ucar.nc2.dataset.CoordinateAxis;
import ucar.nc2.dataset.CoordinateSystem;
import ucar.nc2.dataset.CoordinateTransform;
import ucar.nc2.dataset.NetcdfDataset;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;

public class RunTimeLoadingTutorial {
    public static void IOSPRegister(String yourClassNameAsString) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        ucar.nc2.NetcdfFiles.registerIOProvider( yourClassNameAsString);
    }

    public static void CoordSysBuilderRegister(NetcdfDataset yourNetcdf, List<CoordinateAxis> yourAxes, List<CoordinateTransform> yourTransforms) {
        // no method to register CoordSysBuilder (and isMine method). Instead, I can show how to build CoordSystem
        //ucar.nc2.dataset.CoordinateSystem.Builder.build(  yourNetcdf,  yourAxes, yourTransforms);
    }

    public static void CoordTransBuilderRegister(NetcdfDataset yourNetcdf, List<CoordinateAxis> yourAxes, List<CoordinateTransform> yourTransforms) {
        // Similarly no register method - only set methods and build does nothing
       // ucar.nc2.dataset.CoordinateTransform.Builder.build();
    }
    public static void FeatureDatasetFactoryRegister(FeatureType yourFeatureType, String yourClassNameAsString) {
        ucar.nc2.ft.FeatureDatasetFactoryManager.registerFactory( yourFeatureType, yourClassNameAsString);
    }

    public static void GRIB12LookupTableRegister(String yourFileNameAsString) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        /*ucar.nc2.grib.grib1.Grib1ParamTables.addParameterUserLookup(  yourFileNameAsString);
        ucar.nc2.grib.grib2.ParameterTable.addParametersUser(  yourFileNameAsString);*/
    }

    public static void GRIB1TableRegister(int center, int subcenter, int tableVersion, String yourFileNameAsString) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
       // ucar.nc2.grib.grib1.tables.Grib1ParamTables.addParameterTable( center,  subcenter,  tableVersion,  yourFileNameAsString);
    }

    public static void GRIB1LookupTableRegister(String yourLookupFileNameAsString) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        //ucar.nc2.grib.grib1.tables.Grib1ParamTables.addParameterTableLookup( yourLookupFileNameAsString);
    }

    public static void BUFRTableRegister(String yourLookupFileNameAsString) throws ClassNotFoundException, IllegalAccessException, InstantiationException, FileNotFoundException{
       // ucar.nc2.iosp.bufr.tables.BufrTables.addLookupFile(  yourLookupFileNameAsString) ;
    }

}
