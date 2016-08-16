package com.intershop.component.test.pipelet.test;

public class TestPipelet
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
    public void init()
    {
    }
    
    private boolean getConfigValue(String aString) 
    {
    }
    
}
