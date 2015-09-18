/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sejda.sambox.pdmodel.interactive.form;

import static java.util.Objects.nonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.sejda.sambox.cos.COSArray;
import org.sejda.sambox.cos.COSArrayList;
import org.sejda.sambox.cos.COSBase;
import org.sejda.sambox.cos.COSDictionary;
import org.sejda.sambox.cos.COSName;
import org.sejda.sambox.cos.COSNull;
import org.sejda.sambox.cos.COSNumber;
import org.sejda.sambox.cos.COSObjectable;
import org.sejda.sambox.cos.COSString;
import org.sejda.sambox.pdmodel.PDDocument;
import org.sejda.sambox.pdmodel.PDResources;

/**
 * An interactive form, also known as an AcroForm.
 *
 * @author Ben Litchfield
 */
public final class PDAcroForm implements COSObjectable
{
    private static final int FLAG_SIGNATURES_EXIST = 1;
    private static final int FLAG_APPEND_ONLY = 1 << 1;

    private final PDDocument document;
    private final COSDictionary dictionary;

    private Map<String, PDField> fieldCache;

    /**
     * Constructor.
     *
     * @param doc The document that this form is part of.
     */
    public PDAcroForm(PDDocument doc)
    {
        document = doc;
        dictionary = new COSDictionary();
        dictionary.setItem(COSName.FIELDS, new COSArray());
    }

    /**
     * Constructor.
     *
     * @param doc The document that this form is part of.
     * @param form The existing acroForm.
     */
    public PDAcroForm(PDDocument doc, COSDictionary form)
    {
        document = doc;
        dictionary = form;
    }

    /**
     * This will get the document associated with this form.
     *
     * @return The PDF document.
     */
    PDDocument getDocument()
    {
        return document;
    }

    @Override
    public COSDictionary getCOSObject()
    {
        return dictionary;
    }

    /**
     * This will return all of the documents root fields.
     * 
     * A field might have children that are fields (non-terminal field) or does not have children which are fields
     * (terminal fields).
     * 
     * The fields within an AcroForm are organized in a tree structure. The documents root fields might either be
     * terminal fields, non-terminal fields or a mixture of both. Non-terminal fields mark branches which contents can
     * be retrieved using {@link PDNonTerminalField#getChildren()}.
     * 
     * @return A list of the documents root fields.
     * 
     */
    public List<PDField> getFields()
    {
        List<PDField> pdFields = new ArrayList<>();
        COSArray fields = (COSArray) getCOSObject().getDictionaryObject(COSName.FIELDS);
        if (fields != null)
        {
            for (COSBase field : fields)
            {
                if (!COSNull.NULL.equals(field) && nonNull(field))
                {
                    pdFields.add(PDField.fromDictionary(this, (COSDictionary) field.getCOSObject(),
                            null));
                }
            }
        }
        return pdFields;
    }

    /**
     * Adds the fields to the root fields of the form
     * 
     * @param fields
     */
    public void addFields(List<PDField> toAdd)
    {
        COSArray fields = (COSArray) getCOSObject().getDictionaryObject(COSName.FIELDS);
        if (fields == null)
        {
            fields = new COSArray();
        }
        for (PDField field : toAdd)
        {
            fields.add(field);
        }
        getCOSObject().setItem(COSName.FIELDS, fields);
        Optional.ofNullable(fieldCache).ifPresent(c -> c.clear());
    }

    /**
     * removes the given field from the root fields of the form
     */
    public void removeField(PDField remove)
    {
        COSArray fields = (COSArray) getCOSObject().getDictionaryObject(COSName.FIELDS);
        if (fields != null && fields.contains(remove))
        {
            fields.remove(remove);
            Optional.ofNullable(fieldCache).ifPresent(c -> c.clear());
        }
    }

    /**
     * Set the documents root fields.
     *
     * @param fields The fields that are part of the documents root fields.
     */
    public void setFields(List<PDField> fields)
    {
        dictionary.setItem(COSName.FIELDS, COSArrayList.converterToCOSArray(fields));
    }

    /**
     * Returns an iterator which walks all fields in the field tree, in order.
     */
    public Iterator<PDField> getFieldIterator()
    {
        return new PDFieldTree(this).iterator();
    }

    /**
     * @return the field tree representing all form fields and allowing a post-order visit of the tree
     */
    public PDFieldTree getFieldTree()
    {
        return new PDFieldTree(this);
    }

    /**
     * This will tell this form to cache the fields into a Map structure for fast access via the getField method. The
     * default is false. You would want this to be false if you were changing the COSDictionary behind the scenes,
     * otherwise setting this to true is acceptable.
     *
     * @param cache A boolean telling if we should cache the fields.
     */
    public void setCacheFields(boolean cache)
    {
        if (cache)
        {
            fieldCache = new HashMap<>();
            for (PDField field : getFieldTree())
            {
                fieldCache.put(field.getFullyQualifiedName(), field);
            }
        }
        else
        {
            fieldCache = null;
        }
    }

    /**
     * This will tell if this acro form is caching the fields.
     *
     * @return true if the fields are being cached.
     */
    public boolean isCachingFields()
    {
        return fieldCache != null;
    }

    /**
     * This will get a field by name, possibly using the cache if setCache is true.
     *
     * @param fullyQualifiedName The name of the field to get.
     * @return The field with that name of null if one was not found.
     */
    public PDField getField(String fullyQualifiedName)
    {
        PDField retval = null;
        if (fieldCache != null)
        {
            retval = fieldCache.get(fullyQualifiedName);
        }
        else
        {
            String[] nameSubSection = fullyQualifiedName.split("\\.");
            COSArray fields = (COSArray) dictionary.getDictionaryObject(COSName.FIELDS);

            if (fields != null)
            {
                for (int i = 0; i < fields.size() && retval == null; i++)
                {
                    COSDictionary element = (COSDictionary) fields.getObject(i);
                    if (element != null)
                    {
                        COSString fieldName = (COSString) element.getDictionaryObject(COSName.T);
                        if (fieldName.getString().equals(fullyQualifiedName)
                                || fieldName.getString().equals(nameSubSection[0]))
                        {
                            PDField root = PDField.fromDictionary(this, element, null);
                            if (root != null)
                            {
                                if (nameSubSection.length > 1)
                                {
                                    PDField kid = root.findKid(nameSubSection, 1);
                                    if (kid != null)
                                    {
                                        retval = kid;
                                    }
                                    else
                                    {
                                        retval = root;
                                    }
                                }
                                else
                                {
                                    retval = root;
                                }
                            }
                        }
                    }
                }
            }
        }
        return retval;
    }

    /**
     * Get the default appearance.
     * 
     * @return the DA element of the dictionary object
     */
    public String getDefaultAppearance()
    {
        COSString defaultAppearance = (COSString) dictionary.getItem(COSName.DA);
        return defaultAppearance.getString();
    }

    /**
     * Set the default appearance.
     * 
     * @param daValue a string describing the default appearance
     */
    public void setDefaultAppearance(String daValue)
    {
        dictionary.setString(COSName.DA, daValue);
    }

    /**
     * True if the viewing application should construct the appearances of all field widgets. The default value is
     * false.
     * 
     * @return the value of NeedAppearances, false if the value isn't set
     */
    public boolean getNeedAppearances()
    {
        return dictionary.getBoolean(COSName.NEED_APPEARANCES, false);
    }

    /**
     * Set the NeedAppearances value. If this is false, PDFBox will create appearances for all field widget.
     * 
     * @param value the value for NeedAppearances
     */
    public void setNeedAppearances(Boolean value)
    {
        dictionary.setBoolean(COSName.NEED_APPEARANCES, value);
    }

    /**
     * This will get the default resources for the acro form.
     *
     * @return The default resources.
     */
    public PDResources getDefaultResources()
    {
        PDResources retval = null;
        COSDictionary dr = (COSDictionary) dictionary.getDictionaryObject(COSName.DR);
        if (dr != null)
        {
            retval = new PDResources(dr);
        }
        return retval;
    }

    /**
     * This will set the default resources for the acroform.
     *
     * @param dr The new default resources.
     */
    public void setDefaultResources(PDResources dr)
    {
        dictionary.setItem(COSName.DR, dr);
    }

    /**
     * This will tell if the AcroForm has XFA content.
     *
     * @return true if the AcroForm is an XFA form
     */
    public boolean hasXFA()
    {
        return dictionary.containsKey(COSName.XFA);
    }

    /**
     * This will tell if the AcroForm is a dynamic XFA form.
     *
     * @return true if the AcroForm is a dynamic XFA form
     */
    public boolean xfaIsDynamic()
    {
        return hasXFA() && getFields().isEmpty();
    }

    /**
     * Get the XFA resource, the XFA resource is only used for PDF 1.5+ forms.
     *
     * @return The xfa resource or null if it does not exist.
     */
    public PDXFAResource getXFA()
    {
        PDXFAResource xfa = null;
        COSBase base = dictionary.getDictionaryObject(COSName.XFA);
        if (base != null)
        {
            xfa = new PDXFAResource(base);
        }
        return xfa;
    }

    /**
     * Set the XFA resource, this is only used for PDF 1.5+ forms.
     *
     * @param xfa The xfa resource.
     */
    public void setXFA(PDXFAResource xfa)
    {
        dictionary.setItem(COSName.XFA, xfa);
    }

    /**
     * This will get the 'quadding' or justification of the text to be displayed. 0 - Left(default)<br/>
     * 1 - Centered<br />
     * 2 - Right<br />
     * Please see the QUADDING_CONSTANTS.
     *
     * @return The justification of the text strings.
     */
    public int getQ()
    {
        int retval = 0;
        COSNumber number = (COSNumber) dictionary.getDictionaryObject(COSName.Q);
        if (number != null)
        {
            retval = number.intValue();
        }
        return retval;
    }

    /**
     * This will set the quadding/justification of the text. See QUADDING constants.
     *
     * @param q The new text justification.
     */
    public void setQ(int q)
    {
        dictionary.setInt(COSName.Q, q);
    }

    /**
     * Determines if SignaturesExist is set.
     * 
     * @return true if the document contains at least one signature.
     */
    public boolean isSignaturesExist()
    {
        return dictionary.getFlag(COSName.SIG_FLAGS, FLAG_SIGNATURES_EXIST);
    }

    /**
     * Set the SignaturesExist bit.
     *
     * @param signaturesExist The value for SignaturesExist.
     */
    public void setSignaturesExist(boolean signaturesExist)
    {
        dictionary.setFlag(COSName.SIG_FLAGS, FLAG_SIGNATURES_EXIST, signaturesExist);
    }

    /**
     * Determines if AppendOnly is set.
     * 
     * @return true if the document contains signatures that may be invalidated if the file is saved.
     */
    public boolean isAppendOnly()
    {
        return dictionary.getFlag(COSName.SIG_FLAGS, FLAG_APPEND_ONLY);
    }

    /**
     * Set the AppendOnly bit.
     *
     * @param appendOnly The value for AppendOnly.
     */
    public void setAppendOnly(boolean appendOnly)
    {
        dictionary.setFlag(COSName.SIG_FLAGS, FLAG_APPEND_ONLY, appendOnly);
    }
}
