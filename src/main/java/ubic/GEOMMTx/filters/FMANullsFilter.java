/*
 * The GEOMMTx project
 * 
 * Copyright (c) 2007 University of British Columbia
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package ubic.GEOMMTx.filters;

import java.util.HashSet;
import java.util.Set;

import ubic.GEOMMTx.Vocabulary;
import ubic.basecode.ontology.model.OntologyTerm;
import ubic.basecode.ontology.providers.FMAOntologyService;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Resource;

/**
 * Leon explains what this does: "I think sometimes MMTx would give me links to ontology terms that are not in or null
 * in BIRNLex or FMA. That code would just remove those null terms." Since we don't use BIRNLex any more, we removed
 * that.
 * 
 * @author lfrench
 * @version $Id$
 */
public class FMANullsFilter extends AbstractFilter implements URIFilter {

    FMAOntologyService FMA;

    /**
     * @param fma
     * @param birn
     */
    public FMANullsFilter( FMAOntologyService fma ) {

        this.FMA = fma;

        FMA.startInitializationThread( true );
        while ( !FMA.isOntologyLoaded() ) {
            try {
                Thread.sleep( 2500 );
            } catch ( Exception e ) {
                e.printStackTrace();
            }
        }

    }

    /*
     * (non-Javadoc)
     * 
     * @see ubic.GEOMMTx.filters.URIFilter#accept(java.lang.String)
     */
    @Override
    public boolean accept( String URI ) {
        OntologyTerm term = getTerm( URI );
        // go into FMA and birnlex and check if it's missing
        if ( URI.contains( "/owl/FMA#" ) && term == null ) {
            return false;
        }
        if ( URI.contains( "birnlex" ) && term == null ) {
            return false;
        }
        return true;
    }

    /*
     * (non-Javadoc)
     * 
     * @see ubic.GEOMMTx.filters.AbstractFilter#filter(com.hp.hpl.jena.rdf.model.Model)
     */
    @Override
    public int filter( Model model ) {
        // need a list of all the appearing URL's
        Set<String> removeURIs = new HashSet<String>();

        String queryString = "PREFIX gemmaAnn: <http://bioinformatics.ubc.ca/Gemma/ws/xml/gemmaAnnotations.owl#>\n"
                + "SELECT DISTINCT ?url \n" + "WHERE {\n" + "   ?mention gemmaAnn:"
                + Vocabulary.mappedTerm.getLocalName() + " ?url .\n  " + "}";

        Query q = QueryFactory.create( queryString );
        QueryExecution qexec = QueryExecutionFactory.create( q, model );

        ResultSet results = qexec.execSelect();
        while ( results.hasNext() ) {
            QuerySolution soln = results.nextSolution();
            Resource urlR = soln.getResource( "url" );
            String URI = urlR.getURI();
            // go into FMA and birnlex and check if it's missing
            // if its then add it to the set
            if ( accept( URI ) == false ) {
                removeURIs.add( URI );
                // log.info( URI );
            }
            // else its not FMA or birnlex
        }
        // log.info( "number of null URL's:" + count );
        return removeMentionsURLs( model, removeURIs );
    }

    /*
     * (non-Javadoc)
     * 
     * @see ubic.GEOMMTx.filters.AbstractFilter#getName()
     */
    @Override
    public String getName() {
        return "FMA null mapping remover";
    }

    /**
     * @param uri
     * @return
     */
    private OntologyTerm getTerm( String uri ) {
        return FMA.getTerm( uri );
    }

}
