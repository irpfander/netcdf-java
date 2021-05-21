package tests.runtime;
import examples.cdmdatasets.ReadingCdmTutorial;
import examples.runtime.RunTimeLoadingTutorial;
import org.junit.Assert;
import org.junit.Test;
import ucar.nc2.constants.FeatureType;

import static com.google.common.truth.Truth.assertThat;

public class TestRunTimeLoadingTutorial {

    @Test
    public void testIOSPRegister() {
        // test open success
        Assert.assertThrows(ClassNotFoundException.class, () -> {
            RunTimeLoadingTutorial.IOSPRegister("classname");
        });
    }

    @Test
    public void testFeatureDatasetFactoryRegister() {
        // test open success
        Assert.assertThrows(ClassNotFoundException.class, () -> {
            RunTimeLoadingTutorial.FeatureDatasetFactoryRegister(null,"classname");
        });
    }

}
