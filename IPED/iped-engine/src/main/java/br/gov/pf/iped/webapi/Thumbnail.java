package br.gov.pf.iped.webapi;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.io.IOUtils;
import org.sleuthkit.datamodel.TskCoreException;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import iped3.IIPEDSource;
import iped3.IItem;

@Api(value = "Documents")
@Path("sources/{sourceID}/docs/{id}/thumb")
public class Thumbnail {

    @ApiOperation(value = "Get document's thumbnail")
    @GET
    @Produces("image/jpg")
    public StreamingOutput content(@PathParam("sourceID") String sourceID, @PathParam("id") int id)
            throws TskCoreException, IOException, URISyntaxException {

        IIPEDSource source = Sources.getSource(sourceID);
        IItem item = source.getItemByID(id);
        final byte[] thumb = item.getThumb() != null ? item.getThumb() : new byte[0];
        return new StreamingOutput() {
            @Override
            public void write(OutputStream arg0) throws IOException, WebApplicationException {
                IOUtils.copy(new ByteArrayInputStream(thumb), arg0);
            }
        };
    }
}