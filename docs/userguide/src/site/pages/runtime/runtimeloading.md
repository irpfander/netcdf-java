---
title: Runtime loading
last_updated: 2019-09-14
sidebar: userguide_sidebar
permalink: runtime_loading.html
toc: false
---
## Runtime Loading

These are the various classes that can be plugged in at runtime:

### Register an IOServiceProvider

1) The recommended way is to use the [Service Provider](https://docs.oracle.com/javase/tutorial/ext/basics/spi.html){:target="_blank"}
mechanism and include your IOSP in a jar on the classpath, where it is dynamically loaded at runtime. In your
jar, include a file named `META-INF/services/ucar.nc2.iosp.IOServiceProvider` containing the
name(s) of your implementations, eg:

~~~
ucar.nc2.iosp.fysat.Fysatiosp
ucar.nc2.iosp.gini.Giniiosp
~~~

2) Alternatively, from your code, register your IOSP by calling:

{% capture rmd %}
{% includecodeblock netcdf-java&docs/userguide/src/test/java/examples/runtime/RunTimeLoadingTutorial.java&IOSPRegister %}
{% endcapture %}
{{ rmd | markdownify }}

In both cases, your class must implement `ucar.nc2.IOServiceProvider`. 
When a `NetcdfFiles.open` or `NetcdfDatasets.open` is called, we loop through the `IOServiceProvider` classes and call

~~~java
boolean isValidFile( ucar.unidata.io.RandomAccessFile raf)
~~~

on each, until one returns `true`. This method must be fast and accurate.

### Register a CoordSysBuilder:
~~~java
ucar.nc2.dataset.CoordSysBuilder.registerConvention( String conventionName, String className);
~~~ 
The registered class must implement `ucar.nc2.dataset.CoordSysBuilderIF`. The `NetcdfDataset` is checked if it has a `Convention` attribute, and if so, 
it is matched by `conventionName`. If not, loop through the `CoordSysBuilderIF` classes and call

~~~java
boolean isMine(NetcdfFile ncfile) 
~~~

on each, until one returns `true`. If none are found, use the default `_Coordinate` convention.

### Register a CoordTransBuilder:
~~~java
ucar.nc2.dataset.CoordTransBuilder.registerTransform( String transformName, String className);
~~~

The registered class must implement `ucar.nc2.dataset.CoordTransBuilderIF`. The Coordinate Transform `Variable` must have the transform name as one of its parameters.

### Register a FeatureDatasetFactory:
{% capture rmd %}
{% includecodeblock netcdf-java&docs/userguide/src/test/java/examples/runtime/RunTimeLoadingTutorial.java&FeatureDatasetFactoryRegister %}
{% endcapture %}
{{ rmd | markdownify }}

The registered class must implement `ucar.nc2.ft.FeatureDatasetFactory`.

### Register a GRIB1 or GRIB2 Lookup Table (4.2 and before):
~~~java
ucar.grib.grib1.GribPDSParamTable.addParameterUserLookup( String filename);
ucar.grib.grib2.ParameterTable.addParametersUser( String filename);
~~~  

### Register a GRIB1 table (4.3):
~~~java
ucar.nc2.grib.grib1.tables.Grib1ParamTables.addParameterTable(int center, int subcenter, int tableVersion, String tableFilename);
~~~

This registers a single table for the given center/subcenter/version.
See [GribTables](../developer/grib_tables.html){:target="_blank"} for more information about parameter tables.
*Note:* GRIB2 table handling is still being developed.

### Register a GRIB1 lookup table (4.3):
~~~java
ucar.nc2.grib.grib1.tables.Grib1ParamTables.addParameterTableLookup(String lookupFilename);
~~~

This registers one or more tables for different center/subcenter/versions.
See [GribTables](../developer/grib_tables.html){:target="_blank"} for more information about lookup tables.

*NOTE:* GRIB2 table handling is still being developed.

### Register a BUFR Table lookup:
~~~java
ucar.nc2.iosp.bufr.tables.BufrTables.addLookupFile( String filename) throws throws FileNotFoundException;
~~~

The file must be a [BUFR table lookup file](../developer/bufr_tables.html){:target="_blank"}.

