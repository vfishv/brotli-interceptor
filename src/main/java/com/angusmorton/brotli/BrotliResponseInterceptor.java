package com.angusmorton.brotli;

import com.angusmorton.brotli.internal.BrotliSource;
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.internal.http.HttpHeaders;
import okhttp3.internal.http.RealResponseBody;
import okio.Okio;
import okio.Source;

import java.io.IOException;
import java.util.List;

public class BrotliResponseInterceptor implements Interceptor {

  private static final String HeaderAcceptEncoding = "Accept-Encoding";

  @Override
  public Response intercept(Chain chain) throws IOException {
    Request userRequest = chain.request();
    Request.Builder requestBuilder = userRequest.newBuilder();

    // If we add an "Accept-Encoding: br" header field we're responsible for also decompressing
    // the transfer stream.

    List<String> encodings = userRequest.headers(HeaderAcceptEncoding);
    if (encodings == null || encodings.isEmpty()) {
      requestBuilder.addHeader(HeaderAcceptEncoding, "br");
    } else {
      //Accept-Encoding: gzip, deflate
      requestBuilder.removeHeader(HeaderAcceptEncoding);
      StringBuffer sb = new StringBuffer();
      for (String encoding : encodings) {
        sb.append(encoding).append(", ");
      }
      sb.append("br");
      requestBuilder.addHeader(HeaderAcceptEncoding, sb.toString());
    }

    Response networkResponse = chain.proceed(requestBuilder.build());

    Response.Builder responseBuilder = networkResponse.newBuilder()
        .request(userRequest);

    if ("br".equalsIgnoreCase(networkResponse.header("Content-Encoding"))
        && HttpHeaders.hasBody(networkResponse)) {
      Source brotliSource = BrotliSource.create(networkResponse.body().source());
      Headers strippedHeaders = networkResponse.headers().newBuilder()
          .removeAll("Content-Encoding")
          .removeAll("Content-Length")
          .build();
      responseBuilder.headers(strippedHeaders);
      String contentType = networkResponse.header("Content-Type");
      responseBuilder.body(new RealResponseBody(contentType, -1L, Okio.buffer(brotliSource)));
    }

    return responseBuilder.build();
  }
}


