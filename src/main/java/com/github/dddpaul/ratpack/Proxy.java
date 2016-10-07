package com.github.dddpaul.ratpack;

import ratpack.handling.Context;
import ratpack.http.client.HttpClient;
import ratpack.server.RatpackServer;
import ratpack.server.ServerConfig;
import ratpack.test.embed.EmbeddedApp;

import java.net.URI;

public class Proxy {
    public static void main(String... args) throws Exception {
        EmbeddedApp app = EmbeddedApp.fromServer(
                ServerConfig
                        .builder()
                        .development(false)
                        .port(0)
                        .build(), b -> b.handler(r -> ctx -> ctx.render("OK\n")));

        RatpackServer.of(server -> server
                .serverConfig(c -> c.development(false))
                .handlers(chain -> chain
                        .get("proxy/:query?", ctx -> proxy(ctx, app.getAddress()))
                        .get("proxy-stream/:query?", ctx -> proxyStream(ctx, app.getAddress()))
                )).start();
    }

    /**
     * Non-streaming proxy, upstream response body may be logged.
     * But it has some nasty performance bottleneck and couldn't stand for highload:
     * <pre>
     * $ wrk -d20s -c32 -t32 http://localhost:5050/proxy/
     * Thread Stats   Avg      Stdev     Max   +/- Stdev
     * Latency     4.07ms    3.85ms  70.29ms   91.41%
     * Req/Sec   246.18    110.28   465.00     62.66%
     * 73052 requests in 20.04s, 11.36MB read
     * Socket errors: connect 31, read 0, write 0, timeout 0
     * Requests/sec:   3644.48
     * Transfer/sec:    580.13KB
     * </pre>
     * <pre>
     * $ wrk -d20s -c32 -t32 http://localhost:5050/proxy/
     * Thread Stats   Avg      Stdev     Max   +/- Stdev
     * Latency     2.53ms    2.08ms   5.90ms   87.50%
     * Req/Sec    20.00     20.00    50.00     75.00%
     * 8 requests in 20.10s, 1.27KB read
     * Socket errors: connect 32, read 0, write 0, timeout 0
     * Requests/sec:      0.40
     * Transfer/sec:      64.88B
     * </pre>
     */
    private static void proxy(Context ctx, URI uri) {
        ctx.get(HttpClient.class)
                .request(uri, spec -> spec.getHeaders().copy(ctx.getRequest().getHeaders()))
                .then(resp -> resp.forwardTo(ctx.getResponse()));
    }

    /**
     * Streaming proxy, upstream response body is unavailable.
     * No performance bottleneck.
     */
    private static void proxyStream(Context ctx, URI uri) {
        ctx.get(HttpClient.class)
                .requestStream(uri, spec -> spec.getHeaders().copy(ctx.getRequest().getHeaders()))
                .then(resp -> resp.forwardTo(ctx.getResponse()));
    }
}
