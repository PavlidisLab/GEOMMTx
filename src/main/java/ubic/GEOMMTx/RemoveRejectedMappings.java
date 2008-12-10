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
package ubic.GEOMMTx;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.persister.entity.Loadable;

import ubic.GEOMMTx.evaluation.CUIIRIPair;
import ubic.GEOMMTx.evaluation.CUISUIPair;
import ubic.GEOMMTx.evaluation.EvaluatePhraseToCUISpreadsheet;
import ubic.gemma.ontology.BirnLexOntologyService;
import ubic.gemma.ontology.FMAOntologyService;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;

@Deprecated
public class RemoveRejectedMappings {
    protected static Log log = LogFactory.getLog( RemoveRejectedMappings.class );

    Set<CUISUIPair> rejectedCUISUIPairs;
    Set<CUIIRIPair> rejectedCUIIRIPairs;
    Set<String> frequentURLs;
    FMAOntologyService FMA;
    BirnLexOntologyService BIRN;
    boolean loadOntologies;

    public RemoveRejectedMappings( boolean loadOntologies ) throws Exception {
        this.loadOntologies = loadOntologies;
        
        // CUI -> SUI rejections
        EvaluatePhraseToCUISpreadsheet evalSheet = new EvaluatePhraseToCUISpreadsheet();
        rejectedCUISUIPairs = evalSheet.getRejectedSUIs();

        // CUI -> IRI rejections
        rejectedCUIIRIPairs = SetupParameters.rejectedCUIIRIPairs;

        frequentURLs = new HashSet<String>();
        BufferedReader f = new BufferedReader( new FileReader( SetupParameters.config.getString( "gemma.annotator.uselessFrequentURLsFile")) );
        String line;
        while ( ( line = f.readLine() ) != null ) {
            frequentURLs.add( line );
        }
        f.close();

        if ( loadOntologies ) {
            // load FMA and birnlex
            FMA = new FMAOntologyService();
            BIRN = new BirnLexOntologyService();
            FMA.init( true );
            BIRN.init( true );
            while ( !( FMA.isOntologyLoaded() && BIRN.isOntologyLoaded() ) ) {
                try {
                    Thread.sleep( 2500 );
                } catch ( Exception e ) {
                    e.printStackTrace();
                }
            }
            log.info( "FMA and BIRNLex Ontologies loaded" );
        }
    }

    public int removeFrequentURLs( Model model ) {
        return removeMentionsURLs( model, frequentURLs );
    }

    /**
     * remove mentions that have a mapped term corresponding to one of the given URL's
     * 
     * @param model the rdf model to remove mentions from
     * @param URLs the urls to remove
     * @return
     */
    public int removeMentionsURLs( Model model, Set<String> URLs ) {
        int howMany = 0;
        for ( String URL : URLs ) {
            Resource resource = model.createResource( URL );
            // list all the mentions
            ResIterator mentionIterator = model.listResourcesWithProperty( Vocabulary.mappedTerm, resource );
            Set mentionSet = mentionIterator.toSet();
            ProjectRDFModelTools.removeMentions( model, mentionSet );
            howMany += mentionSet.size();
        }
        return howMany;
    }

    public int removeBIRNLexFMANulls( Model model ) {
        if (loadOntologies== false) {
            log.warn("Cannot remove null target mappings, FMA and BIRNLex not loaded");
            return 0;
        }
        
        // need a list of all the appearing URL's
        Set<String> removeURIs = new HashSet<String>();

        String queryString = "PREFIX gemmaAnn: <http://bioinformatics.ubc.ca/Gemma/ws/xml/gemmaAnnotations.owl#>\n"
                + "SELECT DISTINCT ?url \n                                                                                       "
                + "WHERE {\n                                                                                                 "
                + "   ?mention gemmaAnn:" + Vocabulary.mappedTerm.getLocalName()
                + " ?url .\n                                                                        " + "}";

        Query q = QueryFactory.create( queryString );
        QueryExecution qexec = QueryExecutionFactory.create( q, model );

        int count = 0;

        ResultSet results = qexec.execSelect();
        while ( results.hasNext() ) {
            QuerySolution soln = results.nextSolution();
            Resource urlR = soln.getResource( "url" );
            String URI = urlR.getURI();
            // go into FMA and birnlex and check if it's missing
            // if its then add it to the set
            if ( URI.contains( "/owl/FMA#" ) && FMA.getTerm( URI ) == null ) {
                removeURIs.add( URI );
                log.info( URI );
                count++;
            }
            if ( URI.contains( "birnlex" ) && BIRN.getTerm( URI ) == null ) {
                removeURIs.add( URI );
                log.info( URI );
                count++;
            }
            // else its not FMA or birnlex
        }
        log.info( "number of null URL's:" + count );
        return removeMentionsURLs( model, removeURIs );
    }

    public int removeCUIIRIPairs( Model model ) {
        String queryStringTemplate = "PREFIX gemmaAnn: <http://bioinformatics.ubc.ca/Gemma/ws/xml/gemmaAnnotations.owl#>\n"
                + "SELECT  ?mention ?phrase \n                                                                                       "
                + "WHERE {\n                                                                                                 "
                + "   ?phrase gemmaAnn:hasMention ?mention .\n                                                            "
                + "   ?mention gemmaAnn:"
                + Vocabulary.mappedTerm.getLocalName()
                + " <$IRI> .\n                                                                        "
                + "   ?mention gemmaAnn:hasCUI <$CUI> .\n                                                                        "
                + "}";
        int howMany = 0;
        for ( CUIIRIPair rejected : rejectedCUIIRIPairs ) {
            String queryString = queryStringTemplate;
            queryString = queryString.replace( "$IRI", rejected.IRI );
            queryString = queryString.replace( "$CUI", rejected.CUI );
            // log.info( queryString );

            Query q = QueryFactory.create( queryString );
            QueryExecution qexec = QueryExecutionFactory.create( q, model );

            ResultSet results = qexec.execSelect();
            howMany += removeMentions( model, results );
        }
        return howMany;

    }

    public int removeLowScores( Model model, int minScore ) {
        // query for low score mentions, these ones will be removed
        String queryString = "PREFIX gemmaAnn: <http://bioinformatics.ubc.ca/Gemma/ws/xml/gemmaAnnotations.owl#>\n"
                + "SELECT  ?mention ?score \n                                                                                       "
                + "WHERE {\n                                                                                                 "
                + "   ?mention gemmaAnn:" + Vocabulary.hasScore.getLocalName()
                + " ?score .\n                                 " + "   FILTER (?score <" + minScore
                + ")                                                            " + "}";

        log.info( queryString );

        Query q = QueryFactory.create( queryString );
        QueryExecution qexec = QueryExecutionFactory.create( q, model );

        ResultSet results = qexec.execSelect();
        return removeMentions( model, results );
    }

    /*
     * Goes through RDF and removes pairs that have rejected CUI/SUI combinations
     */
    public int removeCUISUIPairs( Model model ) {
        int howMany = 0;

        // query for SUI CUI combinations

        String queryStringTemplate = "PREFIX gemmaAnn: <http://bioinformatics.ubc.ca/Gemma/ws/xml/gemmaAnnotations.owl#>\n"
                + "SELECT  ?mention ?phrase \n                                                                                       "
                + "WHERE {\n                                                                                                 "
                + "   ?phrase gemmaAnn:hasMention ?mention .\n                                                            "
                + "   ?mention gemmaAnn:hasSUI <$SUI> .\n                                                                        "
                + "   ?mention gemmaAnn:hasCUI <$CUI> .\n                                                                        "
                + "}";

        for ( CUISUIPair rejected : rejectedCUISUIPairs ) {
            String queryString = queryStringTemplate;
            queryString = queryString.replace( "$SUI", rejected.SUI );
            queryString = queryString.replace( "$CUI", rejected.CUI );

            Query q = QueryFactory.create( queryString );
            QueryExecution qexec = QueryExecutionFactory.create( q, model );

            ResultSet results = qexec.execSelect();
            howMany += removeMentions( model, results );
        }
        return howMany;
    }

    private int removeExperimentalFactors( Model model ) {
        String queryString = "PREFIX rdf: <http://www.w3.org/2000/01/rdf-schema#>"
                + "PREFIX gemmaAnn: <http://bioinformatics.ubc.ca/Gemma/ws/xml/gemmaAnnotations.owl#>\n                              "
                + "\n                                                            "
                + "SELECT DISTINCT ?dataset ?description ?mention\n                                                            "
                + "WHERE {\n                                                            "
                + "    ?dataset gemmaAnn:describedBy ?description .\n                                                            "
                + "    ?description gemmaAnn:hasPhrase ?phrase .\n                                                            "
                + "    ?phrase gemmaAnn:hasMention ?mention .\n                                                            "
                + "    FILTER regex(str(?description), \"experimentalFactor\") }";
        Query q = QueryFactory.create( queryString );
        QueryExecution qexec = QueryExecutionFactory.create( q, model );

        ResultSet results = qexec.execSelect();
        return removeMentions( model, results );
    }

    private int removeMentions( Model model, ResultSet results ) {
        Set<Resource> affectedMentions = new HashSet<Resource>();

        while ( results.hasNext() ) {
            QuerySolution soln = results.nextSolution();
            Resource mention = soln.getResource( "mention" );
            affectedMentions.add( mention );
        }

        ProjectRDFModelTools.removeMentions( model, affectedMentions );

        return affectedMentions.size();
    }

    public static void main( String args[] ) throws Exception {
        RemoveRejectedMappings remove = new RemoveRejectedMappings(true);
        // Model model = loadModel( "656.fix.rdf" );
        // System.out.println("CUI+SUI:"+remove.removeCUISUIPairs( model ));
        // System.out.println("CUI+IRI:"+remove.removeCUIIRIPairs( model ));
        // model.write( new FileWriter( "656.rejected.rdf" ) );

        // Model model = Text2OwlModelTools.loadModel( "mergedRDFBirnLexUpdate.rdf" );
        // System.out.println( "Experimental factors:" + remove.removeExperimentalFactors( model ) );
        // model.write( new FileWriter( "mergedRDFBirnLexUpdateNoExp.rdf" ) );
        // log.info( "model wrote" );

        // Model model = Text2OwlModelTools.loadModel( "mergedRDFBirnLexUpdateNoExp.rdf" );
        // System.out.println( "CUI+SUI:" + remove.removeCUISUIPairs( model ) );
        // System.out.println( "CUI+IRI:" + remove.removeCUIIRIPairs( model ) );
        // System.out.println( "Null IRIs:" + remove.removeBIRNLexFMANulls( model ) );
        // model.write( new FileWriter( "mergedRDFBirnLexUpdate.afterrejected.rdf" ) );
        // log.info("model wrote");

        Model model = ProjectRDFModelTools.loadModel( "mergedRDFBirnLexUpdate.afterrejected.rdf" );
        System.out.println( "Frequent Useless URLs:" + remove.removeFrequentURLs( model ) );
        model.write( new FileWriter( "mergedRDFBirnLexUpdate.afterUseless.rdf" ) );
        log.info( "model wrote" );

        // Model model = Text2OwlModelTools.loadModel( "296.fix.rdf" );
        // System.out.println( "Frequent Useless URLs:" + remove.removeFrequentURLs( model ) );
        // model.write( new FileWriter( "296.afterUseless.rdf" ) );

        // Model model = Text2OwlModelTools.loadModel( "mergedRDF.rdf" );
        // System.out.println( "Frequent Useless URLs:" + remove.removeFrequentURLs( model ) );
        // System.out.println( "CUI+SUI:" + remove.removeCUISUIPairs( model ) );
        // System.out.println( "CUI+IRI:" + remove.removeCUIIRIPairs( model ) );
        // model.write( new FileWriter( "mergedRDF.rejected.removed.rdf" ) );

        // Model model = Text2OwlModelTools.loadModel( "mergedRDF.rejected.removed.rdf" );
        // int minScore = 1000;
        // System.out.println( "Below score of " + minScore + ":" + remove.removeLowScores( model, minScore ) );
        // model.write( new FileWriter( "mergedRDF.rejected.removed."+minScore+".rdf" ) );

    }
}
