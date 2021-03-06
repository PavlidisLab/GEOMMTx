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
package ubic.GEOMMTx.evaluation;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import ubic.GEOMMTx.OntologyTools;
import ubic.GEOMMTx.ProjectRDFModelTools;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Model;

/*
 * Given a GEOMMTx RDF mode, find out what descriptions in Gemma lead to a given annotation for a specific experiment 
 */
public class DescriptionExtractor {
    public static void main( String ar[] ) throws Exception {
        DescriptionExtractor de = new DescriptionExtractor( "mergedRDFBirnLexUpdate.afterUseless.rdf" );
        System.out.println( de.getDecriptionURIs( "16", "http://purl.org/obo/owl/FMA#FMA_58301" ) );
        System.out.println( de.getDecriptionType( "16", "http://purl.org/obo/owl/FMA#FMA_58301" ) );
    }

    Model model;

    public DescriptionExtractor( Model model ) {
        this.model = model;
    }

    public DescriptionExtractor( String filename ) throws IOException {
        this.model = ProjectRDFModelTools.loadModel( filename );
    }

    public List<String> getDecriptionType( String experimentID, Set<String> annotationURIs ) {
        List<String> result = new LinkedList<String>();
        for ( String annotationURI : annotationURIs ) {
            result.addAll( getDecriptionType( experimentID, annotationURI ) );
        }
        return result;
    }

    public Set<String> getDecriptionType( String experimentID, String annotationURI ) {
        Set<String> URIs = getDecriptionURIs( experimentID, annotationURI );
        Set<String> result = new HashSet<String>();
        for ( String URI : URIs ) {
            // a regex would make more sense here, or properly encoding the source type in the RDF
            // http://bioinformatics.ubc.ca/Gemma/bioAssay/476/name
            URI = URI.substring( URI.lastIndexOf( "Gemma/" ) + 6 );
            // bioAssay/476/name
            String subName = URI.substring( URI.lastIndexOf( "/" ) );
            URI = URI.substring( 0, URI.lastIndexOf( "/" ) );
            // bioAssay/476/
            URI = URI.substring( 0, URI.lastIndexOf( "/" ) );
            // bioAssay/
            result.add( URI + subName );
        }
        return result;
    }

    public List<String> getDecriptionURIs( String experimentID, Set<String> annotationURIs ) {
        List<String> result = new LinkedList<String>();
        for ( String annotationURI : annotationURIs ) {
            result.addAll( getDecriptionURIs( experimentID, annotationURI ) );
        }
        return result;
    }

    public Set<String> getDecriptionURIs( String experimentID, String annotationURI ) {
        Set<String> result = new HashSet<String>();
        String queryString = "PREFIX rdf: <http://www.w3.org/2000/01/rdf-schema#>"
                + "PREFIX gemmaAnn: <http://bioinformatics.ubc.ca/Gemma/ws/xml/gemmaAnnotations.owl#>\n                              "
                + "SELECT ?phrase ?description ?mention\n                                                            "
                + "WHERE {\n                                                            "
                + "    <http://bioinformatics.ubc.ca/Gemma/experiment/"
                + experimentID
                + "> gemmaAnn:describedBy ?description .\n                                                            "
                + "    ?description gemmaAnn:hasPhrase ?phrase .\n                                                            "
                + "    ?phrase gemmaAnn:hasMention ?mention .\n                                                           "
                + "      ?mention gemmaAnn:mappedTerm <" + annotationURI
                + "> .\n                                                            " + " }";
        Query q = QueryFactory.create( queryString );
        QueryExecution qexec = QueryExecutionFactory.create( q, model );
        ResultSet results = qexec.execSelect();
        while ( results.hasNext() ) {
            QuerySolution qTemp = results.nextSolution();
            String desc = OntologyTools.varToString( "description", qTemp );
            result.add( desc );
        }
        return result;
    }
}
