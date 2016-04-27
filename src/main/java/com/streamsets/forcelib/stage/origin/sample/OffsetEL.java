package com.streamsets.forcelib.stage.origin.sample;

import com.streamsets.pipeline.api.ElConstant;

public class OffsetEL {
    @ElConstant(name = "OFFSET", description = "")
    public static final String OFFSET = "${offset}";

    private OffsetEL() {}
}
