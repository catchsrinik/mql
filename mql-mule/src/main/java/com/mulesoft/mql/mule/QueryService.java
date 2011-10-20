/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package com.mulesoft.mql.mule;

import org.mule.api.MuleContext;
import org.mule.api.MuleException;
import org.mule.api.MuleMessage;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.api.processor.MessageProcessorChainBuilder;
import org.mule.api.routing.filter.Filter;
import org.mule.api.source.MessageSource;
import org.mule.construct.AbstractPipeline;
import org.mule.module.json.transformers.JsonToObject;
import org.mule.module.json.transformers.ObjectToJson;
import org.mule.processor.NullMessageProcessor;
import org.mule.processor.ResponseMessageProcessorAdapter;
import org.mule.routing.ChoiceRouter;
import org.mule.transformer.types.DataTypeFactory;
import org.mule.transport.http.HttpConnector;
import org.mule.transport.http.transformers.FormTransformer;

public class QueryService extends AbstractPipeline {

    
    private final String query;
    private final Type type;

    public QueryService(String name, String query, 
                        Type type,
                        MessageSource messageSource, MuleContext muleContext) {
        super(name, muleContext);
        this.type = type;
        this.query = query;
        this.messageSource = messageSource;
    }

    @Override
    protected void configureMessageProcessors(MessageProcessorChainBuilder builder) throws MuleException {
        if (type == Type.JSON) {
            createJsonTransformers(builder);
        }
        
        Filter formFilter = getFormFilter();
        FormTransformer formTransformer = new FormTransformer();
        ChoiceRouter choiceRouter = new ChoiceRouter();
        choiceRouter.addRoute(formTransformer, formFilter);
        
        MqlTransformer mqlTransformer = new MqlTransformer();
        mqlTransformer.setQuery(query);
        builder.chain(mqlTransformer);
    }

    protected void createJsonTransformers(MessageProcessorChainBuilder builder) throws InitialisationException {
        final JsonToObject jsonToObject = new JsonToObject();
        jsonToObject.setReturnDataType(DataTypeFactory.create(Object.class));
        
        ChoiceRouter choiceRouter = new ChoiceRouter();
        choiceRouter.addRoute(jsonToObject, getJsonFilter());
        choiceRouter.setDefaultRoute(new NullMessageProcessor());
        builder.chain(choiceRouter);
        builder.chain(new ResponseMessageProcessorAdapter(new ObjectToJson()));
    }

    protected Filter getJsonFilter() {
        Filter formFilter = new Filter() {
            
            public boolean accept(MuleMessage msg) {
                Object ct = msg.getInboundProperty("Content-Type");
                String method = (String)msg.getInboundProperty(HttpConnector.HTTP_METHOD_PROPERTY);

                if ((ct == null && (method == null || !method.toUpperCase().equals("GET")))
                        || (ct != null && ct.toString().contains("application/json"))) {
                    return true;
                }
                return false;
            }
        };
        return formFilter;
    }

    protected Filter getFormFilter() {
        Filter formFilter = new Filter() {
            
            public boolean accept(MuleMessage msg) {
                Object ct = msg.getInboundProperty("Content-Type");
                if (ct != null && ct.toString().contains("multipart/form-data")) {
                    return true;
                }
                return false;
            }
        };
        return formFilter;
    }

    @Override
    public String getConstructType() {
        return "Query-Service";
    }

}
