/*
 * The Gemma project
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
package ubic.GEOMMTx.mappers;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import ubic.GEOMMTx.CUIMapper;
import ubic.gemma.ontology.OntologyLoader;
import ubic.gemma.ontology.OntologyTools;

import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;

public class DiseaseOntologyMapper extends AbstractToUMLSMapper implements CUIMapper {

    public static void main( String args[] ) {
        DiseaseOntologyMapper test = new DiseaseOntologyMapper();
        // test.loadFromOntology();
        // test.save();
        // /String cui =

        System.out.println( test.convert( "C0020492", null ) );
        System.out.println( "CUI's that have more that one URI:" + test.countOnetoMany() );
        System.out.println( "All urls size:" + test.getAllURLs().size() );
    }

    private OntModel model;

    public DiseaseOntologyMapper() {
        super();
    }

    @Override
    public String getMainURL() {
        return "http://www.berkeleybop.org/ontologies/obo-all/disease_ontology/disease_ontology.owl";
    }

    @Override
    public void loadFromOntology() {
        CUIMap = new HashMap<String, Set<String>>();

        // load the ontology model
        try {
            model = OntologyLoader.loadMemoryModel( getMainURL() );
        } catch ( Exception e ) {
            e.printStackTrace();
            System.exit( 1 );
        }

        String queryString = "PREFIX oboInOwl: <http://www.geneontology.org/formats/oboInOwl#>                                                    \r\n"
                + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>                                                    \r\n"
                + "SELECT ?obj ?label ?dbcode                                                    \r\n"
                + "WHERE  {                                                               \r\n"
                + "    ?anon rdfs:label ?dbcode .                                                    \r\n"
                + "    ?obj oboInOwl:hasDbXref ?anon .                                                    \r\n"
                + "    ?obj rdfs:label ?label .                                                    \r\n"
                + "    FILTER (REGEX(?dbcode, \"UMLS_CUI:\"))                                                    \r\n"
                + "}";

        Query q = QueryFactory.create( queryString );
        QueryExecution qexec = QueryExecutionFactory.create( q, model );
        try {
            ResultSet results = qexec.execSelect();
            while ( results.hasNext() ) {
                QuerySolution soln = results.nextSolution();
                // String label = OntologyTools.varToString( "label", soln );
                String URI = OntologyTools.varToString( "obj", soln );
                String cui = OntologyTools.varToString( "dbcode", soln );

                // UMLS_CUI:C00123 is split and we use the second half
                cui = cui.split( ":" )[1];

                Set<String> URIs = CUIMap.get( cui );
                if ( URIs == null ) {
                    URIs = new HashSet<String>();
                    CUIMap.put( cui, URIs );
                }
                URIs.add( URI );

                /*
                 * System.out.print( label + " " ); System.out.println( cui + " " ); System.out.println( URI + " " );
                 */
                //                
                // if ( x.isAnon() ) continue; // some reasoners will return these.
            }
        } finally {
            qexec.close();
        }
    }

}
