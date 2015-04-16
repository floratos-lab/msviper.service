package org.geworkbench.service.msviper.service; 

import org.geworkbench.service.msviper.schema.MsViperInput;
import org.geworkbench.service.msviper.schema.MsViperOutput;

import java.io.IOException;

public interface MsViper {

    String storeMsViperInput(MsViperInput input) throws IOException;

    MsViperOutput execute(MsViperInput input, String dataDir) throws IOException;
}
