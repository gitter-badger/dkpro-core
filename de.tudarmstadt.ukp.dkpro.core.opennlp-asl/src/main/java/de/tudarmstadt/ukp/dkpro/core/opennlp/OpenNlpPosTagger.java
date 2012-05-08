/*******************************************************************************
 * Copyright 2012
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package de.tudarmstadt.ukp.dkpro.core.opennlp;

import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.uimafit.util.JCasUtil.select;
import static org.uimafit.util.JCasUtil.selectCovered;
import static org.uimafit.util.JCasUtil.toText;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;

import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTagger;
import opennlp.tools.postag.POSTaggerME;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.Type;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;

import de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.POS;
import de.tudarmstadt.ukp.dkpro.core.api.parameter.ComponentParameters;
import de.tudarmstadt.ukp.dkpro.core.api.resources.MappingProvider;
import de.tudarmstadt.ukp.dkpro.core.api.resources.CasConfigurableProviderBase;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;

/**
 * Part-of-Speech annotator using OpenNLP. Requires {@link Sentence}s to be annotated before.
 * 
 * @author Richard Eckart de Castilho
 */
public class OpenNlpPosTagger
	extends JCasAnnotator_ImplBase
{
	public static final String PARAM_LANGUAGE = ComponentParameters.PARAM_LANGUAGE;
	@ConfigurationParameter(name = PARAM_LANGUAGE, mandatory = false)
	protected String language;

	public static final String PARAM_VARIANT = "variant";
	@ConfigurationParameter(name = PARAM_VARIANT, mandatory = false)
	protected String variant;

	public static final String PARAM_MODEL_LOCATION = ComponentParameters.PARAM_MODEL_LOCATION;
	@ConfigurationParameter(name = PARAM_MODEL_LOCATION, mandatory = false)
	protected String modelLocation;

	public static final String PARAM_MAPPING_LOCATION = "mappingLocation";
	@ConfigurationParameter(name = PARAM_MAPPING_LOCATION, mandatory = false)
	protected String mappingLocation;

	public static final String PARAM_INTERN_STRINGS = "InternStrings";
	@ConfigurationParameter(name = PARAM_INTERN_STRINGS, mandatory = false, defaultValue = "true")
	private boolean internStrings;

	private CasConfigurableProviderBase<POSTagger> modelProvider;
	private MappingProvider mappingProvider;
	
	@Override
	public void initialize(UimaContext aContext)
		throws ResourceInitializationException
	{
		super.initialize(aContext);

		mappingProvider = new MappingProvider();
		mappingProvider.setDefault(MappingProvider.LOCATION, "classpath:/de/tudarmstadt/ukp/dkpro/" +
				"core/api/lexmorph/tagset/${language}-tagger.map");
		mappingProvider.setDefault(MappingProvider.BASE_TYPE, POS.class.getName());
		mappingProvider.setOverride(MappingProvider.LOCATION, mappingLocation);
		mappingProvider.setOverride(MappingProvider.LANGUAGE, language);
		
		modelProvider = new CasConfigurableProviderBase<POSTagger>() {
			{
				setDefault(LOCATION, "classpath:/de/tudarmstadt/ukp/dkpro/core/opennlp/lib/" +
						"tagger-${language}-${variant}.bin");
				setDefault(VARIANT, "maxent");
				
				setOverride(LOCATION, modelLocation);
				setOverride(LANGUAGE, language);
				setOverride(VARIANT, variant);
			}
			
			@Override
			protected POSTagger produceResource(URL aUrl) throws IOException
			{
				InputStream is = null;
				try {
					is = aUrl.openStream();
					POSModel model = new POSModel(is);
					return new POSTaggerME(model);
				}
				finally {
					closeQuietly(is);
				}
			}
		};
	}

	@Override
	public void process(JCas aJCas)
		throws AnalysisEngineProcessException
	{
		CAS cas = aJCas.getCas();

		modelProvider.configure(cas);
		mappingProvider.configure(cas);
				
		for (Sentence sentence : select(aJCas, Sentence.class)) {
			List<Token> tokens = selectCovered(aJCas, Token.class, sentence);
			String[] tokenTexts = toText(tokens).toArray(new String[tokens.size()]);

			String[] tags = modelProvider.getResource().tag(tokenTexts);

			int i = 0;
			for (Token t : tokens) {
				Type posTag = mappingProvider.getTagType(tags[i]);
				POS posAnno = (POS) cas.createAnnotation(posTag, t.getBegin(), t.getEnd());
				posAnno.setStringValue(posTag.getFeatureByBaseName("PosValue"),
						internStrings ? tags[i].intern() : tags[i]);
				posAnno.addToIndexes();
				t.setPos((POS) posAnno);
				i++;
			}
		}
	}
}