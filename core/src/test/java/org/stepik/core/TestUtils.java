package org.stepik.core;

import org.jdom.Element;
import org.jdom.input.DOMBuilder;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

import static org.junit.Assert.assertNotNull;
import static org.stepik.core.utils.ProjectFilesUtils.SEPARATOR;

/**
 * @author meanmail
 */
public class TestUtils {
    @NotNull
    public static String join(@NotNull CharSequence... elements) {
        return String.join(SEPARATOR, elements);
    }

    @NotNull
    public static Element readXmlFile(Class<?> clazz, @NotNull String filename)
            throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(clazz.getResourceAsStream(filename));
        DOMBuilder domBuilder = new DOMBuilder();

        Element root = domBuilder.build(doc).getRootElement();
        assertNotNull("Failed read test xml data file", root);

        return root;
    }
}
