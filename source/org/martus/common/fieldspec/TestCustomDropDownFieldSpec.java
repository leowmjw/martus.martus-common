/*

The Martus(tm) free, social justice documentation and
monitoring software. Copyright (C) 2005, Beneficent
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

package org.martus.common.fieldspec;

import java.util.Vector;

import org.martus.util.TestCaseEnhanced;

public class TestCustomDropDownFieldSpec extends TestCaseEnhanced
{
	public TestCustomDropDownFieldSpec(String name)
	{
		super(name);
	}

	public void testMandatoryBlankEntry()
	{
		CustomDropDownFieldSpec spec = new CustomDropDownFieldSpec();

		Vector valuesWithoutBlank = new Vector();
		valuesWithoutBlank.add("one");
		valuesWithoutBlank.add("two");
		spec.setChoices(valuesWithoutBlank);
		assertEquals("didn't add a blank entry?", valuesWithoutBlank.size() + 1, spec.getCount());
		assertEquals("", spec.getValue(0));
		for(int i=0; i < valuesWithoutBlank.size(); ++i)
			assertEquals("wrong order?", valuesWithoutBlank.get(i), spec.getValue(i+1));
		
		assertEquals("two", spec.getValue(spec.findCode("two")));
		
		
		Vector valuesWithBlank = new Vector();
		valuesWithBlank.add("a");
		valuesWithBlank.add("b");
		valuesWithBlank.add("");
		spec.setChoices(valuesWithBlank);
		assertEquals("added a blank entry?", valuesWithBlank.size(), spec.getCount());
		for(int i=0; i < spec.getCount(); ++i)
			assertEquals("wrong order?", valuesWithBlank.get(i), spec.getValue(i));
	}

	public void testXml() throws Exception
	{
		FieldSpec.XmlFieldSpecLoader loader = new FieldSpec.XmlFieldSpecLoader();
		loader.parse(SAMPLE_DROPDOWN_FIELD_XML);
		DropDownFieldSpec spec = (DropDownFieldSpec)loader.getFieldSpec();
		assertEquals(3, spec.getCount());
		assertEquals("", spec.getValue(0));
		assertEquals(SAMPLE_DROPDOWN_CHOICE1, spec.getValue(1));
		assertEquals(SAMPLE_DROPDOWN_CHOICE2, spec.getValue(2));
		assertEquals(SAMPLE_DROPDOWN_FIELD_XML, spec.toString());
		try
		{
			spec.getValue(3);
			fail("Should have thrown");
		}
		catch(ArrayIndexOutOfBoundsException expected)
		{
		}
	}
	
	public static final String SAMPLE_DROPDOWN_CHOICE1 = "choice #1";
	public static final String SAMPLE_DROPDOWN_CHOICE2 = "choice #2";
	public static final String SAMPLE_DROPDOWN_LABEL = "Dropdown Label";
	public static final String SAMPLE_DROPDOWN_FIELD_XML = "<Field type='DROPDOWN'>\n" +
			"<Tag>custom</Tag>\n" +
			"<Label>"+SAMPLE_DROPDOWN_LABEL+"</Label>\n" +
			"<Choices>\n" +
			"<Choice>" +
			"</Choice>\n" +
			"<Choice>" +
			SAMPLE_DROPDOWN_CHOICE1 +
			"</Choice>\n" +
			"<Choice>" +
			SAMPLE_DROPDOWN_CHOICE2 +
			"</Choice>\n" +
			"</Choices>\n" +
			"</Field>\n";
}
