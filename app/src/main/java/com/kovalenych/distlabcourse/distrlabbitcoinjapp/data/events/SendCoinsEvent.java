package com.kovalenych.distlabcourse.distrlabbitcoinjapp.data.events;

import com.squareup.okhttp.Response;

import java.io.IOException;

/**
 * Created by Dima Kovalenko on 9/18/17.
 */

public class SendCoinsEvent {
    private final boolean _isSuccess;
    private final Response _response;
    private final IOException _e;

    public SendCoinsEvent(boolean isSuccess, Response response, IOException e) {

        _isSuccess = isSuccess;
        _response = response;
        _e = e;
    }

    public boolean isSuccess() {
        return _isSuccess;
    }

    public Response getResponse() {
        return _response;
    }

    public IOException getE() {
        return _e;
    }
}
