/* Copyright 2017-present The KotlinNLP Authors. All Rights Reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * ------------------------------------------------------------------*/

package com.kotlinnlp.tokensencoder.embeddings

import com.beust.klaxon.internal.firstNotNullResult
import com.kotlinnlp.linguisticdescription.sentence.Sentence
import com.kotlinnlp.linguisticdescription.sentence.token.Token
import com.kotlinnlp.simplednn.core.arrays.ParamsArray
import com.kotlinnlp.simplednn.core.neuralprocessor.NeuralProcessor
import com.kotlinnlp.simplednn.core.optimizer.ParamsErrorsAccumulator
import com.kotlinnlp.simplednn.core.optimizer.ParamsErrorsList
import com.kotlinnlp.simplednn.simplemath.ndarray.dense.DenseNDArray
import com.kotlinnlp.tokensencoder.TokensEncoder

/**
 * The [TokensEncoder] that encodes a token using the word embeddings
 *
 * @property model the model of this tokens encoder
 * @property id an identification number useful to track a specific processor
 */
class EmbeddingsEncoder<TokenType: Token, SentenceType: Sentence<TokenType>>(
  override val model: EmbeddingsEncoderModel<TokenType, SentenceType>,
  override val id: Int = 0
) : TokensEncoder<TokenType, SentenceType>() {

  companion object {

    /**
     * Calculate the dropout probability of an element with a proportionality that is inverse respect to its
     * occurrences.
     *
     * Examples:
     *
     *  dropout_coeff = 1.0, occurrences = 1 -> prob = 0.50
     *  dropout_coeff = 1.0, occurrences = 2 -> prob = 0.33
     *  dropout_coeff = 1.0, occurrences = 3 -> prob = 0.25
     *
     *  dropout_coeff = 0.5, occurrences = 1 -> prob = 0.33
     *  dropout_coeff = 0.5, occurrences = 2 -> prob = 0.20
     *  dropout_coeff = 0.5, occurrences = 3 -> prob = 0.14
     *
     *  dropout_coeff = 0.25, occurrences = 1 -> prob = 0.20
     *  dropout_coeff = 0.25, occurrences = 2 -> prob = 0.11
     *  dropout_coeff = 0.25, occurrences = 3 -> prob = 0.08
     *
     * @param occurrences the number of occurrences of an element
     * @param dropoutCoefficient the dropout coefficient
     *
     * @return the dropout probability
     */
    private fun dropoutProbability(occurrences: Int, dropoutCoefficient: Double): Double =
      when {
        dropoutCoefficient > 0.0 -> dropoutCoefficient / (occurrences + dropoutCoefficient)
        else -> 0.0
      }
  }

  /**
   * The word embeddings of the last encoded sentence.
   */
  private lateinit var lastEmbeddings: List<ParamsArray>

  /**
   * The errors accumulated during the last backward.
   */
  private var lastEmbeddingsErrors = ParamsErrorsAccumulator()

  /**
   * Encode a list of tokens.
   *
   * @param input an input sentence
   *
   * @return a list of dense encoded representations of the given sentence tokens
   */
  override fun forward(input: SentenceType): List<DenseNDArray> {

    this.lastEmbeddings = (0 until input.tokens.size).map { tokenIndex ->

      this.getEmbeddingKey(input, tokenIndex).let { key ->

        this.model.embeddingsMap.get(key = key, dropout = this.getDropout(key))
      }
    }

    return this.lastEmbeddings.map { it.values }
  }

  /**
   * Get the dropout probability of the [key]-element in relation to its occurrences in the dictionary of frequencies.
   *
   * @param key the key of the element to find in the dictionary
   *
   * @return the dropout probability
   */
  private fun getDropout(key: String?): Double =
    if (false)
      0.0
    else
      this.model.frequencyDictionary?.let { dict ->
        dropoutProbability(
          occurrences = key?.let { dict[key] } ?: 0, // 0 if not in the dictionary
          dropoutCoefficient = this.model.dropout)
      } ?: this.model.dropout

  /**
   * Propagate the errors.
   *
   * @param outputErrors the errors of the current encoding
   */
  override fun backward(outputErrors: List<DenseNDArray>) {

    require(outputErrors.size == this.lastEmbeddings.size)

    this.lastEmbeddingsErrors.clear()

    this.lastEmbeddings.zip(outputErrors).forEach { (embedding, errors) ->

      this.lastEmbeddingsErrors.accumulate(embedding, errors)
    }
  }

  /**
   * @param copy a Boolean indicating whether the returned errors must be a copy or a reference
   *
   * @return the errors of the model parameters
   */
  override fun getParamsErrors(copy: Boolean): ParamsErrorsList =
    this.lastEmbeddingsErrors.getParamsErrors(copy)

  /**
   * @param copy whether to return by value or by reference
   *
   * @return the input errors of the last backward
   */
  override fun getInputErrors(copy: Boolean) = NeuralProcessor.NoInputErrors

  /**
   * @param sentence a generic sentence
   * @param tokenIndex the id index the token from which to extract the key
   *
   * @return the string to use as embedding key (can be null)
   */
  private fun getEmbeddingKey(sentence: SentenceType, tokenIndex: Int): String? =
    this.model.keyExtractors.firstNotNullResult {
      it.getKey(sentence, tokenIndex).let { key ->
        if (key in this.model.embeddingsMap) key else null
      }
    }
}
