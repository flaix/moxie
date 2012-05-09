package com.maxtk.tests;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.junit.Assert;
import org.junit.Test;

import com.maxtk.Config;
import com.maxtk.maxml.Maxml;
import com.maxtk.maxml.MaxmlException;
import com.maxtk.maxml.MaxmlMap;
import com.maxtk.maxml.MaxmlParser;
import com.maxtk.utils.FileUtils;

/**
 * Unit tests for the Maxml parser.
 * 
 * @author James Moger
 * 
 */
public class TestMaxmlParser extends Assert {
	String config = "name: Maxilla\ndescription: Project Build Toolkit\nversion: 0.1.0\nurl: http://github.com/gitblit/maxilla\nartifactId: maxilla\nvendor: James Moger\nconfigureEclipseClasspath: true\nsourceFolders: [core, maxjar, maxdoc]\nmap: { \na1: 12\na2: 3.14f\na3 : {\nb1:100l\nb2 : {\nc1:6.023d\nc2:c2value\n}\nb3:b3value\n}\na4: a4value\n}\noutputFolder: bin\nmavenUrls: [mavencentral]\ndependencyFolder: ext\ndependencies:\n - [ant, 1.7.0, org/apache/ant]\n - [markdownpapers-core, 1.2.5, org/tautua/markdownpapers]\nsimpledate:2003-07-04\ncanonical:2001-07-04T16:08:56.235Z\niso8601:2002-07-04T12:08:56.235-0400";

	String blockTest = "name: Maxilla\ndescription: \"\"\"\nMaxilla\nis a\nJava Project Build Toolkit\n\"\"\"\nversion: 0.1.0";

	String blockTest2 = "name: Maxilla\ndescription:\n\"\"\"\nMaxilla\n is a\n  Java Project Build Toolkit\"\"\"\nversion: 0.1.0";
	
	String inlineMap = "{ id: myproxy, active: true, protocol: http, host:proxy.somewhere.com, port:8080, username: proxyuser, password: somepassword }";

	@SuppressWarnings("rawtypes")
	@Test
	public void testValueParsing() throws Exception {
		MaxmlParser parser = new MaxmlParser();
		// strings
		assertEquals("string", parser.parseValue("string"));
		assertEquals("string", parser.parseValue("'string'"));
		assertEquals("string", parser.parseValue("\"string\""));
		// assertEquals("Maxilla\n is a\n  Java Project Build Toolkit", Maxml
		// .parse(blockTest).get("description"));
		// assertEquals("Maxilla\n is a\n  Java Project Build Toolkit", Maxml
		// .parse(blockTest2).get("description"));

		// numerics
		assertEquals(101, parser.parseValue("101"));
		assertEquals(8, parser.parseValue("0x8"));
		assertEquals(8, parser.parseValue("010"));
		assertEquals(3405691582L, parser.parseValue("0xcafebabe"));
		assertEquals(3405691582L, parser.parseValue("#cafebabe"));
		assertEquals(1101, parser.parseValue("1,101"));
		assertEquals(1101202, parser.parseValue("1,101,202"));
		assertEquals(4000000000L, parser.parseValue("4000000000"));
		assertEquals(4000000000L, parser.parseValue("4,000,000,000"));
		assertEquals(2.3f, parser.parseValue("2.3"));
		assertEquals(6.0E23f, parser.parseValue("6.0E+23"));
		assertEquals(6.0E56d, parser.parseValue("6.0E+56"));

		// booleans
		assertEquals(true, parser.parseValue("true"));
		assertEquals(true, parser.parseValue("yes"));
		assertEquals(true, parser.parseValue("on"));

		assertEquals(false, parser.parseValue("false"));
		assertEquals(false, parser.parseValue("no"));
		assertEquals(false, parser.parseValue("off"));

		// null
		assertNull(parser.parseValue("~"));

		// inline lists
		assertEquals("[a, b, c]", parser.parseValue("[a, b, c]").toString());
		assertEquals("[a, b, c]", parser.parseValue("['a', 'b', 'c']")
				.toString());
		assertEquals("[a, b, c]", parser.parseValue("[\"a\", \"b\", \"c\"]")
				.toString());
		assertEquals(3, ((List) parser.parseValue("[a, b, c]")).size());

		assertEquals(3,
				((List) parser.parseValue("[a, \"b, or beta\", c]")).size());
		
		// inline map
		assertEquals(7, ((MaxmlMap) parser.parseValue(inlineMap)).size());

		// dates
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
		assertEquals("2003-07-04",
				df.format(((Date) parser.parseValue("2003-07-04"))));

		df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		assertEquals("2003-07-04T15:15:15Z",
				df.format(((Date) parser.parseValue("2003-07-04T15:15:15Z"))));

		df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
		df.setTimeZone(TimeZone.getTimeZone("America/New_York"));
		assertEquals("2003-07-04T15:15:15-0400", df.format(((Date) parser
				.parseValue("2003-07-04T15:15:15-0400"))));
	}

	@SuppressWarnings("rawtypes")
	@Test
	public void testParse() throws MaxmlException {
		Map<String, Object> map = Maxml.parse(config);
		assertTrue(map.size() > 0);
		assertTrue(map.containsKey("name"));
		assertEquals("Maxilla", map.get("name"));
		assertEquals(3, ((List) map.get("sourcefolders")).size());
		assertEquals(2, ((List) map.get("dependencies")).size());
		assertEquals(4, ((Map) map.get("map")).size());
	}

	@SuppressWarnings("rawtypes")
	@Test
	public void testParseFile() throws MaxmlException {
		String content = FileUtils.readContent(new File("build.maxml"), "\n");
		Map<String, Object> map = Maxml.parse(content);
		assertTrue(map.size() > 0);
		assertTrue(map.containsKey("name"));
		assertEquals("Maxilla", map.get("name"));
		assertEquals(5, ((List) map.get("sourcefolders")).size());
		assertEquals(4, ((List) map.get("dependencies")).size());
	}

	@Test
	public void testConfig() throws Exception {
		Config config = Config.load(new File("build.maxml"), false);
		assertEquals("Maxilla", config.getPom().name);
		assertEquals(5, config.getSourceFolders().size());
	}

	@Test
	public void testInstantiation() throws MaxmlException {
		TestObject test = Maxml.parse(config, TestObject.class);
		assertNotNull(test);
		assertEquals("Maxilla", test.name);
		assertEquals(3, test.sourceFolders.size());
		assertEquals(2, test.dependencies.size());
		assertEquals(4, test.map.size());
	}

	public static class TestObject {

		public String name;
		public String description;
		public String version;
		public String url;
		public String artifactId;
		public String vendor;
		public boolean configureEclipseClasspath;
		public List<String> sourceFolders;
		public String outputFolder;
		public Map<String, Object> map;
		public List<String> mavenUrls;
		public String dependencyFolder;
		public List<List<String>> dependencies;
		public Date canonical;
		public Date iso8601;
		public Date simpleDate;
	}
}
