package com.intershop.component.test.pipelet.test;

import com.intershop.beehive.core.capi.paging.PageableIterator;
import com.intershop.beehive.core.capi.pipeline.Pipelet;
import com.intershop.beehive.core.capi.pipeline.PipeletExecutionException;
import com.intershop.beehive.core.capi.pipeline.PipelineDictionary;
import com.intershop.beehive.core.capi.pipeline.PipelineInitializationException;
import com.intershop.component.mvc.capi.product.ServiceTypeBO;
import com.intershop.component.product.capi.ProductBO;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TestPipelet extends Pipelet
{

    /**
     * Constant used to access the pipeline dictionary with key 'Products'
     */
    public static final String DN_PRODUCTS = "Products";

    /**
     * Constant used for output in the pipeline dictionary with key 'FilteredProducts'
     */
    public static final String DN_FILTERED_PRODUCTS = "FilteredProducts";


    /**
     *  Config values
     */
    private boolean excludeBundleProducts;
    private boolean excludeBundledProducts;
    private boolean excludeMasterProducts;
    private boolean excludeMasteredProducts;
    private boolean excludeRetailSets;
    private boolean excludePartOfRetailSets;
    private boolean excludeServiceTypes;
    
    @Override
    public void init() throws PipelineInitializationException
    {
        
        excludeBundleProducts = getConfigValue("ExcludeBundleProducts");
        excludeBundledProducts = getConfigValue("ExcludeBundledProducts");
        excludeMasterProducts = getConfigValue("ExcludeMasterProducts");
        excludeMasteredProducts = getConfigValue("ExcludeMasteredProducts");
        excludeRetailSets = getConfigValue("ExcludeRetailSets");
        excludePartOfRetailSets = getConfigValue("ExcludePartOfRetailSets");
        excludeServiceTypes = getConfigValue("ExcludeServiceTypes");
    }
    
    private boolean getConfigValue(String aString) 
    {
        String configStr = (String)getConfiguration().get(aString);
        return Boolean.valueOf(configStr).booleanValue();
    }
    
    public int execute(PipelineDictionary dict) throws PipeletExecutionException
    {
        List<ProductBO> filteredProducts = new ArrayList<ProductBO>();
        
        // lookup 'Products' in pipeline dictionary
        Iterator<ProductBO> products = dict.getRequired(DN_PRODUCTS);
        if (products instanceof PageableIterator) 
        {
            ((PageableIterator)products).setPageSize(-1);    
        }
        
        while (products.hasNext())
        {
            ProductBO product = products.next();
        
            boolean typeFound = false;
            if (excludeBundledProducts && product.isBundled()) {
                continue;
            }
            if (excludeMasteredProducts && product.isMastered()) {
                continue;
            }
            if (excludeBundleProducts && product.isProductBundle()) {
                continue;
            }
            if (excludeMasterProducts && product.isProductMaster()) {
                continue;
            }
            if (excludeRetailSets && (product.getTypeCode() & 128) == 128) { 
                //Retail Set
                continue;
            }
            if (excludePartOfRetailSets && (product.getTypeCode() & 256) == 256) { 
                //Part of Retail Set
                continue;
            }
            ServiceTypeBO serviceTypeBO = product.getServiceTypeBO();
            if (excludeServiceTypes && serviceTypeBO != null)
            {
                // Warranty etc.
                continue;
            }
            // gone through all filters -> finally add this to result list
            if (!typeFound && (product.isProductItem() || product.isOffer()))
            {
                filteredProducts.add(product);
            }
        
        }
        dict.put(DN_FILTERED_PRODUCTS, filteredProducts.iterator());
        return PIPELET_NEXT;
    }

}
