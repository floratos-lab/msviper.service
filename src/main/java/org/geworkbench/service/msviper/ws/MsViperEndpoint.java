package org.geworkbench.service.msviper.ws;

import java.io.IOException;

import javax.activation.DataHandler;
import javax.xml.bind.JAXBElement;

import org.springframework.util.Assert;
import org.geworkbench.service.msviper.service.MsViper;
import org.geworkbench.service.msviper.schema.MsViperInput;
import org.geworkbench.service.msviper.schema.ObjectFactory;
import org.geworkbench.service.msviper.schema.MsViperOutput;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
public class MsViperEndpoint {

    private MsViper msviper;

    private ObjectFactory objectFactory;

    public MsViperEndpoint(MsViper msviper) {
        Assert.notNull(msviper, "'msviperInput' must not be null");
        this.msviper = msviper;
        this.objectFactory = new ObjectFactory();
    }

    @PayloadRoot(localPart = "MsViperRequest", namespace = "http://www.geworkbench.org/service/msviper")
    @ResponsePayload
    public JAXBElement<MsViperOutput> executeMsViper(@RequestPayload JAXBElement<MsViperInput> requestElement) throws IOException {
    	
    	MsViperInput input = requestElement.getValue();

        String dataDir = msviper.storeMsViperInput(input);
       
        MsViperOutput response = msviper.execute(input, dataDir);
 
        return objectFactory.createMsViperResponse(response);
    }
}
