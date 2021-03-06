import org.ncgr.pubmed_morphia_example.AuthorObject;
import org.ncgr.pubmed_morphia_example.PubMedSummaryObject;

import org.ncgr.pubmed.PubMedSummary;

import com.mongodb.MongoClient;

import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Morphia;

import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import javax.xml.parsers.ParserConfigurationException;
    
/**
 * Class that does the work of looking up a publication and storing it in MongoDB using Morphia, with a handy main method.
 */
public final class PubHandler {

    public static final String DB_NAME = "pubmed_morphia_example";

    /**
     * Find and store a PubMed article in the MongoDB database, referenced by PMID.
     */
    public static void main(final String[] args) throws UnknownHostException, IOException, ParserConfigurationException, SAXException {

        // initialize args
        int pmid = 0;
        
        // validation
        boolean failedValidation = false;
        if (args.length==1) {
            try {
                pmid = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                failedValidation = true;
            }
        } else {
            failedValidation = true;
        }
        if (failedValidation) {
            System.err.println("Usage: PubHandler <PMID [int]>");
            System.exit(1);
        }

        // it's all about Morphia
        final Morphia morphia = new Morphia();
    
        // tell Morphia where to find your classes
        // can be called multiple times with different packages or classes
        morphia.mapPackage("org.ncgr.pubmed_morphia_example");
        
        // create the Datastore connecting to the default port on the local host
        final Datastore datastore = morphia.createDatastore(new MongoClient(), DB_NAME);
        datastore.ensureIndexes();

        // is it already in the database? if so, get it.
        List<PubMedSummaryObject> pmsoList = datastore.createQuery(PubMedSummaryObject.class).field("_id").equal(new Long((long)pmid)).asList();
        if (pmsoList.size()>0) {
            PubMedSummaryObject pmso = pmsoList.get(0);
            // output
            prettyPrint("Publication "+pmid+" already exists in "+DB_NAME+"."+morphia.getMapper().getCollectionName(pmso));
            System.out.println(pmso.toString());
        } else {
            // get a PubMedSummary from the Interwebs
            PubMedSummary pms = new PubMedSummary(pmid);
            if (pms.id==0) {
                // output
                prettyPrint("Publication "+pmid+" was not found on PubMed.");
            } else {
                PubMedSummaryObject pmso = new PubMedSummaryObject(pms);
                // check which authors are new, which are old, based on name
                Map<AuthorObject,AuthorObject> authorsToReplace = new HashMap<AuthorObject,AuthorObject>();
                for (AuthorObject author : pmso.getAuthorList()) {
                    List<AuthorObject> oneAuthorList = datastore.createQuery(AuthorObject.class).field("name").equal(author.getName()).asList();
                    if (oneAuthorList.size()>0) {
                        // author already exists, set aside the old/new pair for replacement below
                        AuthorObject newAuthor = (AuthorObject) oneAuthorList.get(0);
                        authorsToReplace.put(author, newAuthor);
                    } else {
                        // store the new author
                        datastore.save(author);
                    }
                }
                // replace the authors in pmso that are already in the DB
                for (AuthorObject oldAuthor : authorsToReplace.keySet()) {
                    AuthorObject newAuthor = authorsToReplace.get(oldAuthor);
                    pmso.replaceAuthorObject(oldAuthor, newAuthor);
                }
                // this only saves the pmso, not its authors
                datastore.save(pmso);
                // output
                prettyPrint("Publication "+pmid+" was saved to "+DB_NAME+"."+morphia.getMapper().getCollectionName(pmso));
                System.out.println(pmso.toString());
            }
        }

    }

    /**
     * Pretty print output to terminal.
     */
    static void prettyPrint(String foo) {
        String stars = "******************************************************************************";
        System.out.println();
        System.out.println(stars);
        System.out.println(foo);
        System.out.println(stars);
    }


}
    
