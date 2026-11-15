package net.cozygielinor.runelite;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

final class BridgeHttp
{
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient client;

    @Inject
    BridgeHttp(OkHttpClient client)
    {
        this.client = client;
    }

    static RequestBody json(String value)
    {
        return RequestBody.create(JSON, value);
    }

    void send(Request request, int timeoutSeconds, boolean readBody, Result result)
    {
        Call call = client.newCall(request);
        call.timeout().timeout(timeoutSeconds, TimeUnit.SECONDS);
        call.enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException failure)
            {
                result.accept(0, null);
            }

            @Override
            public void onResponse(Call call, Response response)
            {
                String body;
                try (Response received = response)
                {
                    body = readBody && received.isSuccessful() && received.body() != null
                        ? received.body().string() : null;
                }
                catch (IOException failure)
                {
                    result.accept(0, null);
                    return;
                }
                result.accept(response.code(), body);
            }
        });
    }

    interface Result
    {
        void accept(int status, String body);
    }
}
