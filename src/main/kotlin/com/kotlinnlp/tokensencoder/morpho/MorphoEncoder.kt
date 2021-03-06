/* Copyright 2017-present The KotlinNLP Authors. All Rights Reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * ------------------------------------------------------------------*/

package com.kotlinnlp.tokensencoder.morpho

import com.kotlinnlp.linguisticdescription.sentence.MorphoSentence
import com.kotlinnlp.linguisticdescription.sentence.token.FormToken
import com.kotlinnlp.simplednn.core.neuralprocessor.NeuralProcessor
import com.kotlinnlp.simplednn.core.neuralprocessor.batchfeedforward.BatchFeedforwardProcessor
import com.kotlinnlp.simplednn.core.optimizer.ParamsErrorsList
import com.kotlinnlp.simplednn.simplemath.ndarray.Shape
import com.kotlinnlp.simplednn.simplemath.ndarray.dense.DenseNDArray
import com.kotlinnlp.simplednn.simplemath.ndarray.sparsebinary.SparseBinaryNDArray
import com.kotlinnlp.simplednn.simplemath.ndarray.sparsebinary.SparseBinaryNDArrayFactory
import com.kotlinnlp.tokensencoder.TokensEncoder

/**
 * The [TokensEncoder] that encodes each token of a sentence using its morphological properties.
 *
 * @property model the model of this tokens encoder
 * @property id an identification number useful to track a specific processor
 */
class MorphoEncoder(
  override val model: MorphoEncoderModel,
  override val id: Int = 0
) : TokensEncoder<FormToken, MorphoSentence<FormToken>>() {

  /**
   * The feed-forward network used to transform the input from sparse to dense.
   */
  private val encoder: BatchFeedforwardProcessor<SparseBinaryNDArray> =
    BatchFeedforwardProcessor(model = this.model.denseEncoder, propagateToInput = false)

  /**
   * Encode a list of tokens.
   *
   * @param input an input sentence
   *
   * @return a list of dense encoded representations of the given sentence tokens
   */
  override fun forward(input: MorphoSentence<FormToken>): List<DenseNDArray> {

    val tokenFeatures: List<Set<String>> =
      FeaturesExtractor(sentence = input, lexicalDictionary = this.model.lexiconDictionary).extractFeatures()

    return this.encoder.forward(
      input = tokenFeatures.map {
        SparseBinaryNDArrayFactory.arrayOf(
          activeIndices = it.getActiveFeaturesIndices(),
          shape = Shape(this.model.featuresDictionary.size))
      }
    )
  }

  /**
   * The Backward.
   *
   * @param outputErrors the errors of the current encoding
   */
  override fun backward(outputErrors: List<DenseNDArray>) = this.encoder.backward(outputErrors)

  /**
   * @param copy a Boolean indicating whether the returned errors must be a copy or a reference
   *
   * @return the errors of the model parameters
   */
  override fun getParamsErrors(copy: Boolean): ParamsErrorsList = this.encoder.getParamsErrors(copy = copy)

  /**
   * @param copy whether to return by value or by reference
   *
   * @return the input errors of the last backward
   */
  override fun getInputErrors(copy: Boolean) = NeuralProcessor.NoInputErrors

  /**
   * Map the features set to the features ids using the featuresDictionary.
   *
   * @return the indexes of the active features
   */
  private fun Set<String>.getActiveFeaturesIndices(): List<Int> {

    val activeIndices = mutableListOf<Int>()

    this.forEach { this@MorphoEncoder.model.featuresDictionary.getId(it)?.let {
      featureId -> activeIndices.add(featureId)
    } }

    return activeIndices
  }
}
