/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.bsc.functional;

/**
 *
 * @author softphone
 * @param <P>
 */
public final  class ORFilters<P> implements Filter<P> {

    Filter<P> filters[];
    
    public ORFilters( Filter<P> ... filters) {
        this.filters = (Filter<P>[])filters;
    }

    @Override
    public Boolean f(P p) {

        if( filters==null || filters.length==0 ) {
            return true; // Skip
        }
        
        for( Filter<P> f : filters ) {
            if( f.f(p) ) {
                return true;
            }
        }
        
        return false;
    }
    
    
    
}
