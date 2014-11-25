package argus.document;

import argus.langdetect.LanguageDetectorException;
import argus.langdetect.LanguageDetectorFactory;
import argus.util.Constants;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO
 *
 * @author Eduardo Duarte (<a href="mailto:eduardo.miguel.duarte@gmail.com">eduardo.miguel.duarte@gmail.com</a>)
 * @version 1.0
 * @since 1.0
 */
public class DocumentBuilderTest {

    private static final Logger logger = LoggerFactory.getLogger(DocumentBuilderTest.class);


    @BeforeClass
    public static void setUp() throws LanguageDetectorException {
        LanguageDetectorFactory.loadProfile(Constants.LANGUAGE_PROFILES_DIR);
    }



    // without stopwords and without stemming

    @Test
    public void testHTMLNoStopNoStem() {
        logger.info("testHTMLNoStopNoStem");
        DocumentBuilder
                .fromUrl("http://en.wikipedia.org/wiki/Argus_Panoptes")
                .ignoreCase()
                .build();
        logger.info("--------------------");
    }

    @Test
    public void testXMLNoStopNoStem() {
        logger.info("testXMLNoStopNoStem");
        DocumentBuilder
                .fromUrl("http://en.wikipedia.org/wiki/Special:Export/Argus_Panoptes")
                .ignoreCase()
                .build();
        logger.info("--------------------");
    }

    @Test
    public void testJSONNoStopNoStem() {
        logger.info("testJSONNoStopNoStem");
        DocumentBuilder
                .fromUrl("http://en.wikipedia.org/w/api.php?format=json&action=query&titles=Argus_Panoptes&prop=revisions&rvprop=content")
                .ignoreCase()
                .build();
        logger.info("--------------------");
    }



    // with stopwords and without stemming

    @Test
    public void testHTMLStopNoStem() {
        logger.info("testHTMLStopNoStem");
        DocumentBuilder
                .fromUrl("http://en.wikipedia.org/wiki/Argus_Panoptes")
                .ignoreCase()
                .withStopwords()
                .build();
        logger.info("--------------------");
    }

    @Test
    public void testXMLStopNoStem() {
        logger.info("testXMLStopNoStem");
        DocumentBuilder
                .fromUrl("http://en.wikipedia.org/wiki/Special:Export/Argus_Panoptes")
                .ignoreCase()
                .withStopwords()
                .build();
        logger.info("--------------------");
    }

    @Test
    public void testJSONStopNoStem() {
        logger.info("testJSONStopNoStem");
        DocumentBuilder
                .fromUrl("http://en.wikipedia.org/w/api.php?format=json&action=query&titles=Argus_Panoptes&prop=revisions&rvprop=content")
                .ignoreCase()
                .withStopwords()
                .build();
        logger.info("--------------------");
    }



    // with stopwords and with stemming

    @Test
    public void testHTMLStopStem() {
        logger.info("testHTMLStopStem");
        DocumentBuilder
                .fromUrl("http://en.wikipedia.org/wiki/Argus_Panoptes")
                .ignoreCase()
                .withStopwords()
                .withStemming()
                .build();
        logger.info("--------------------");
    }

    @Test
    public void testXMLStopStem() {
        logger.info("testXMLStopStem");
        DocumentBuilder
                .fromUrl("http://en.wikipedia.org/wiki/Special:Export/Argus_Panoptes")
                .ignoreCase()
                .withStopwords()
                .withStemming()
                .build();
        logger.info("--------------------");
    }

    @Test
    public void testJSONStopStem() {
        logger.info("testJSONStopStem");
        DocumentBuilder
                .fromUrl("http://en.wikipedia.org/w/api.php?format=json&action=query&titles=Argus_Panoptes&prop=revisions&rvprop=content")
                .ignoreCase()
                .withStopwords()
                .withStemming()
                .build();
        logger.info("--------------------");
    }
}
