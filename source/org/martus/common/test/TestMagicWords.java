/*

The Martus(tm) free, social justice documentation and
monitoring software. Copyright (C) 2001-2004, Beneficent
Technology, Inc. (Benetech).

Martus is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either
version 2 of the License, or (at your option) any later
version with the additions and exceptions described in the
accompanying Martus license file entitled "license.txt".

It is distributed WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, including warranties of fitness of purpose or
merchantability.  See the accompanying Martus License and
GPL license for more details on the required license terms
for this software.

You should have received a copy of the GNU General Public
License along with this program; if not, write to the Free
Software Foundation, Inc., 59 Temple Place - Suite 330,
Boston, MA 02111-1307, USA.

*/
package org.martus.common.test;

import java.io.File;
import java.util.Vector;

import org.martus.common.LoggerToConsole;
import org.martus.common.MagicWordEntry;
import org.martus.common.MagicWords;
import org.martus.util.UnicodeReader;
import org.martus.util.UnicodeWriter;


public class TestMagicWords extends TestCaseEnhanced
{
	public TestMagicWords(String name)
	{
		super(name);
	}

	public void setUp() throws Exception
	{
		super.setUp();
		tempFile = createTempFileFromName("$$$MartusTestFileMagicWords");
		UnicodeWriter writer = new UnicodeWriter(tempFile);
		writer.writeln(MAGICWORD1 + MagicWords.FIELD_DELIMITER + GROUPNAME1);
		writer.writeln(MAGICWORD2);
		writer.writeln(INACTIVE_MAGICWORD3 + MagicWords.FIELD_DELIMITER + INACTIVE_GROUPNAME3);
		writer.close();
		
		magicWords = new MagicWords( new LoggerToConsole());	
		magicWords.loadMagicWords(tempFile);	
		tempFile.delete();
	}

	public void tearDown() throws Exception
	{		
		tempFile.delete();
		super.tearDown();
	}
	
	public void testMagicWords() throws Exception
	{
		assertTrue("available active magic words?", magicWords.getActiveMagicWords().size()==2);
		assertTrue("available inactive magic words?", magicWords.getInactiveMagicWords().size()==1);
		
		assertFalse("Not a valid magic word?", magicWords.isValidMagicWord("Test4"));
		assertTrue("A valid magic word", magicWords.isValidMagicWord(MAGICWORD1));
				
	}
	
	public void testActiveAndInActiveMagicWords() throws Exception
	{				
		Vector activeWord = magicWords.getActiveMagicWords();
		assertTrue("Contain size of active magic words", activeWord.size()==2);
		Vector inActiveWord = magicWords.getInactiveMagicWords();
		assertTrue("COntain size of inactive magic words", inActiveWord.size()==1);
	
	}
	
	public void testRemoveMagicWords() throws Exception
	{				
		String removeString = MAGICWORD1;
		
		assertTrue("Contain this matic word", magicWords.isValidMagicWord(removeString));
		assertTrue("Current magic words size", magicWords.size()== 3); 
		magicWords.remove(removeString);
		assertTrue("Current magic words size after remove", magicWords.size()== 2); 			
	
	}
	
	public void testNullMagicWord() throws Exception
	{
		String duplicateString = null;
		assertTrue("Current size?", magicWords.size()==3);
		magicWords.add(duplicateString, null);
		assertFalse("Same size?", magicWords.size()==3);
	}
	
	public void testDuplicateMagicWords() throws Exception
	{
		String duplicateString = MAGICWORD2;
		assertTrue("Current size?", magicWords.size()==3);
		magicWords.add(duplicateString, null);
		assertTrue("Same size?", magicWords.size()==3);
	}

	public void testGetAllMagicWords()
	{
		Vector allMagicWords = magicWords.getAllMagicWords();
		assertEquals("Size incorrect", 3, allMagicWords.size());
		Vector magicWords = new Vector();
		Vector groupNames = new Vector();
		
		for(int i = 0; i<allMagicWords.size(); ++i)
		{
			String lineEntry = (String)allMagicWords.get(i);
			magicWords.add(MagicWords.getMagicWordWithActiveSignFromLineEntry(lineEntry));
			groupNames.add(MagicWords.getGroupNameFromLineEntry(lineEntry));
		}
		assertContains(MAGICWORD1, magicWords);
		assertContains(MAGICWORD2, magicWords);
		assertContains(INACTIVE_MAGICWORD3, magicWords);
		
		assertContains(GROUPNAME1, groupNames);
		assertContains(MAGICWORD2, groupNames);
		assertContains(INACTIVE_GROUPNAME3, groupNames);
	}
	
	public void testGetActiveMagicWords()
	{
		Vector activeMagicWords = magicWords.getActiveMagicWords();
		assertEquals("Size incorrect", 2, activeMagicWords.size());
		Vector magicWords = new Vector();
		Vector groupNames = new Vector();
		
		for(int i = 0; i<activeMagicWords.size(); ++i)
		{
			String lineEntry = (String)activeMagicWords.get(i);
			magicWords.add(MagicWords.getMagicWordWithActiveSignFromLineEntry(lineEntry));
			groupNames.add(MagicWords.getGroupNameFromLineEntry(lineEntry));
		}
		assertContains(MAGICWORD1, magicWords);
		assertContains(MAGICWORD2, magicWords);
		
		assertContains(GROUPNAME1, groupNames);
		assertContains(MAGICWORD2, groupNames);
	}
	
	public void testGetInactiveMagicWords()
	{
		Vector inactiveMagicWords = magicWords.getInactiveMagicWords();
		assertEquals("Size incorrect", 1, inactiveMagicWords.size());
		String lineEntry = (String)inactiveMagicWords.get(0);
		String magicWord = MagicWords.getMagicWordWithActiveSignFromLineEntry(lineEntry);
		String groupName = MagicWords.getGroupNameFromLineEntry(lineEntry);
		
		assertEquals(INACTIVE_MAGICWORD3, magicWord);
		
		assertEquals(INACTIVE_GROUPNAME3, groupName);
	}
	
	public void testWriteoutMagicWordsToFile() throws Exception
	{			
		Vector wordsLineEntry = magicWords.getAllMagicWords();
		magicWords.writeMagicWords(tempFile, wordsLineEntry);
		
		MagicWords magicWords2 = new MagicWords( new LoggerToConsole());	
		magicWords2.loadMagicWords(tempFile);
		
	
		Vector inactiveMagicWordsLineEntry = magicWords2.getInactiveMagicWords();
		assertEquals(INACTIVE_MAGICWORD3, MagicWords.getMagicWordWithActiveSignFromLineEntry((String)inactiveMagicWordsLineEntry.get(0)));	

		Vector activeMagicWordsLineEntry = magicWords2.getActiveMagicWords();
		assertEquals("We should still have magic word #1", 2, activeMagicWordsLineEntry.size());
		Vector groupNames = new Vector();
		for(int i = 0; i<activeMagicWordsLineEntry.size(); ++i)
		{
			String group = MagicWords.getGroupNameFromLineEntry((String)activeMagicWordsLineEntry.get(i));
			groupNames.add(group);
		}
		assertContains(GROUPNAME1, groupNames);
	}	

	public void testLowLevelWriteoutMagicWordsToFile() throws Exception
	{			
		File tempFile1 = createTempFileFromName("$$$MartusTestFileMagicWordsLowLevel");
		UnicodeWriter writer = new UnicodeWriter(tempFile1);
		String lineEntry1 = MAGICWORD1 + MagicWords.FIELD_DELIMITER + GROUPNAME1;
		writer.writeln(lineEntry1);
		writer.writeln(MAGICWORD2);
		String lineEntry3 = INACTIVE_MAGICWORD3 + MagicWords.FIELD_DELIMITER + INACTIVE_GROUPNAME3;
		writer.writeln(lineEntry3);
		writer.writeln(INACTIVE_MAGICWORD4);
		writer.close();
		
		MagicWords magicWords3 = new MagicWords( new LoggerToConsole());	
		magicWords3.loadMagicWords(tempFile1);
		tempFile1.delete();
		
		assertEquals("Size incorrect for all words", 4, magicWords3.getAllMagicWords().size());
		assertEquals("Size incorrect for active words", 2, magicWords3.getActiveMagicWords().size());
		assertEquals("Size incorrect for inactive words", 2, magicWords3.getInactiveMagicWords().size());
		File tempFile2 = createTempFileFromName("$$$MartusTestSaveMagicFile");
		magicWords3.writeMagicWords(tempFile2, magicWords3.getAllMagicWords());

		UnicodeReader reader = new UnicodeReader(tempFile2);
		String line1 = reader.readLine();
		assertEquals("Line 1 should match since we had all fields originally", lineEntry1, line1);
		String line2 = reader.readLine();
		assertEquals("Line 2 should have magicword2 as its group name", MAGICWORD2 + MagicWords.FIELD_DELIMITER +MAGICWORD2, line2);
		String line3 = reader.readLine();
		assertEquals("Line 3 should match since we had all fields originally", lineEntry3, line3);
		String line4 = reader.readLine();
		assertEquals("Line 4 should have magicword4 as its group name", INACTIVE_MAGICWORD4 + MagicWords.FIELD_DELIMITER +MagicWords.filterActiveSign(INACTIVE_MAGICWORD4), line4);
		assertNull("There shouldn't be a line 5", reader.readLine());
		reader.close();
		tempFile2.delete();
	}	
	
	public void testMagicWordsFileLineEntryAdd() throws Exception
	{
		String multiFieldLine = "this is a good test	group2";
		magicWords.add(multiFieldLine);
		assertTrue("Doesn't contain new magic word", magicWords.isValidMagicWord("this is a good test"));
		assertTrue("Should also match a case insensitive search", magicWords.isValidMagicWord("This is A goodTEST"));
	}
	
	public void testNormalizeMagicWord()
	{
		String originalHumanReadableMagicWord = "This Is A Good Magic WORD";
		String normalizedMagicWord = "thisisagoodmagicword";
		assertEquals("Not normalized?", normalizedMagicWord, MagicWords.normalizeMagicWord(originalHumanReadableMagicWord));
		assertEquals("Not normalized?", normalizedMagicWord, MagicWords.normalizeMagicWord("  "+originalHumanReadableMagicWord+" \t "));
	}
	
	public void testMagicWordsFileLineEntryGetMagicWordGroup()
	{
		String magicWord = "magic Words";
		String groupName = "group to use";
		String multiFieldLine = magicWord + "	" + groupName;
		assertEquals("didn't get back correct magic words", magicWord, MagicWords.getMagicWordWithActiveSignFromLineEntry(multiFieldLine));
		assertEquals("didn't get back correct group", groupName, MagicWords.getGroupNameFromLineEntry(multiFieldLine));

		assertEquals("didn't get back correct magic words when not given a group", magicWord, MagicWords.getMagicWordWithActiveSignFromLineEntry(magicWord));
		assertEquals("didn't get back correct group when not given a group", magicWord, MagicWords.getGroupNameFromLineEntry(magicWord));

		assertEquals("didn't get back correct empty string when null", "", MagicWords.getMagicWordWithActiveSignFromLineEntry(null));
		assertEquals("didn't get back correct empty string when null", "", MagicWords.getGroupNameFromLineEntry(null));
	}
	
	public void testFilterActiveSign()
	{
		assertEquals("Didn't filter # sign", "test", MagicWords.filterActiveSign("#test"));
		assertEquals("Didn't get back original string", "test", MagicWords.filterActiveSign("test"));
	}
	
	public void testGetLineEntryFromMagicWordEntry()
	{
		String groupEntry = "group";
		String validMagicWord = "magic";
		MagicWordEntry entry = new MagicWordEntry(validMagicWord, groupEntry);
		String validLineEntry = validMagicWord + MagicWords.FIELD_DELIMITER + groupEntry;
		assertEquals("invalid line entry", validLineEntry, MagicWords.getLineEntryFromMagicWordEntry(entry));
		
	}
	
	private static final String MAGICWORD1 = "TeSt 1";
	private static final String MAGICWORD2 = "test 2";
	private static final String INACTIVE_MAGICWORD3 = "#test 3";
	private static final String INACTIVE_MAGICWORD4 = "#TeSt 4";
	
	private static final String GROUPNAME1 = "Group 1";
	private static final String INACTIVE_GROUPNAME3 = "Group 3";
	
	File tempFile;
	MagicWords magicWords;
}
