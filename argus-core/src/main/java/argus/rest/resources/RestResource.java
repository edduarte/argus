package argus.rest.resources;

import argus.Context;
import argus.rest.WatchRequest;
import argus.rest.CancelRequest;
import argus.rest.RestResponse;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.concurrent.ExecutionException;

/**
 * REST Resource for calls on path "/rest/".
 *
 * @author Eduardo Duarte (<a href="mailto:eduardo.miguel.duarte@gmail.com">eduardo.miguel.duarte@gmail.com</a>)
 * @version 1.0
 * @since 1.0
 */
@Path("/")
public class RestResource {

    private static final Logger logger = LoggerFactory.getLogger(RestResource.class);

    @GET
    @Path("example")
    @Produces(MediaType.APPLICATION_JSON)
    public String test() {
        WatchRequest watchRequest = new WatchRequest(
                "www.example.com/url/to/watch",
                "http://your.site/async-response-receiver",
                Lists.newArrayList("single-word-keyword", "multiple word keyword"),
                600
        );
        return new Gson().toJson(watchRequest);
    }

    @POST
    @Path("testResponse")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response response(String responseJSON) {
        System.out.println("received Argus response: " + responseJSON);
        return Response.ok().build();
    }

    @POST
    @Path("watch")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public String watch(String watchRequestJson) {
        try {
            WatchRequest watchRequest = new Gson()
                    .fromJson(watchRequestJson, WatchRequest.class);

            Context context = Context.getInstance();
            boolean created = context.createJob(watchRequest);
            if (created) {
                RestResponse response = new RestResponse(RestResponse.Code.ok, "");
                return response.toString();
            } else {
                RestResponse response = new RestResponse(RestResponse.Code.error, ""+
                        "The request conflicts with a currently active watch " +
                        "job, since the provided document url is already being " +
                        "watched and notified to the provided response url!");
                return response.toString();
            }

        } catch (JsonSyntaxException ex) {
            // the job-request json had an invalid format
            RestResponse response = new RestResponse(RestResponse.Code.error, ""+
                    "The request has an invalid format. Must provide a message " +
                    "with the following format:\n" +
                    "{\"documentUrl\": <url-to-watch>, " +
                    "\"keywords\": <keywords-to-watch-for>, " +
                    "\"interval\": <interval-in-seconds>, " +
                    "\"responseUrl: <url-to-send-async-responses-to>}");
            return response.toString();
        }
    }

    @POST
    @Path("cancel")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public String cancel(String cancelRequestJson) throws ExecutionException {
        try {
            CancelRequest cancelRequest = new Gson()
                    .fromJson(cancelRequestJson, CancelRequest.class);

            Context context = Context.getInstance();
            context.cancelJob(cancelRequest.documentUrl, cancelRequest.responseUrl);
            RestResponse response = new RestResponse(RestResponse.Code.ok, "");
            return response.toString();

        } catch (JsonSyntaxException ex) {
            // the cancel-request json had an invalid format
            RestResponse response = new RestResponse(RestResponse.Code.error, ""+
                    "The request has an invalid format. Must provide a message " +
                    "with the following format:\n" +
                    "{\"documentUrl\": <url-to-cancel>, " +
                    "\"responseUrl: <url-to-cancel>}");
            return response.toString();
        }
    }
}