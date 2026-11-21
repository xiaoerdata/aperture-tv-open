package org.zhixiaoce.aperture;

import java.util.*;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

/** Validate every segment/redirect and prevent credential propagation to a different origin. */
final class CinemaStreamHttp {
    static OkHttpClient client(String origin,boolean allowHttp){
        HttpUrl root=HttpUrl.get(origin);
        return new OkHttpClient.Builder().connectTimeout(8,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS).retryOnConnectionFailure(true).followRedirects(true).followSslRedirects(true).addNetworkInterceptor(chain->{
            Request request=chain.request();CinemaCatalog.url(request.url().toString(),null,allowHttp);HttpUrl url=request.url();
            if(!root.scheme().equals(url.scheme())||!root.host().equals(url.host())||root.port()!=url.port()){
                Request.Builder safe=request.newBuilder();for(String header:request.headers().names())if(header.equalsIgnoreCase("Authorization")||header.equalsIgnoreCase("Cookie")||header.toLowerCase(Locale.ROOT).startsWith("x-"))safe.removeHeader(header);request=safe.build();
            }
            return chain.proceed(request);
        }).build();
    }
}
