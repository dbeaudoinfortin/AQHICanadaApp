package com.dbf.aqhi.geomet.data;

import com.dbf.aqhi.geomet.Response;
import java.util.List;

public abstract class DataResponse<D extends Data> extends Response {

    public abstract List<D> getData();
}
