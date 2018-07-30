/* Copyright 2017-present The KotlinNLP Authors. All Rights Reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * ------------------------------------------------------------------*/

package com.kotlinnlp.tokensencoder.wrapper

import com.kotlinnlp.linguisticdescription.sentence.Sentence
import com.kotlinnlp.linguisticdescription.sentence.token.Token
import com.kotlinnlp.simplednn.core.neuralprocessor.NeuralProcessor
import com.kotlinnlp.simplednn.simplemath.ndarray.dense.DenseNDArray
import com.kotlinnlp.tokensencoder.TokensEncoder
import com.kotlinnlp.tokensencoder.TokensEncoderParameters

/**
 * A wrapper of a [TokensEncoder] that combines it with a [SentenceConverter], to expand its usage with another kind of
 * input sentence.
 *
 * @property encoder a generic tokens encoder
 * @property converter a sentence converter compatible with the [encoder]
 * @property useDropout whether to apply the dropout
 * @property id an identification number useful to track a specific processor
 */
class TokensEncoderWrapper<
  InTokenType: Token,
  InSentenceType: Sentence<InTokenType>,
  OutTokenType: Token,
  OutSentenceType: Sentence<OutTokenType>
  >(
  val encoder: TokensEncoder<OutTokenType, OutSentenceType>,
  val converter: SentenceConverter<InTokenType, InSentenceType, OutTokenType, OutSentenceType>,
  override val id: Int = 0
) : NeuralProcessor<
  InSentenceType, // InputType
  List<DenseNDArray>, // OutputType
  List<DenseNDArray>, // ErrorsType
  NeuralProcessor.NoInputErrors, // InputErrorsType
  TokensEncoderParameters // ParamsType
  > {

  /**
   * Not used for this processor.
   */
  override val propagateToInput: Boolean = false

  /**
   * Not used for this processor.
   */
  override val useDropout: Boolean = false

  /**
   * Encode a list of tokens.
   *
   * @param input an input sentence
   *
   * @return a list of dense encoded representations of the given sentence tokens
   */
  override fun forward(input: InSentenceType): List<DenseNDArray> = this.encoder.forward(this.converter.convert(input))

  /**
   * The Backward.
   *
   * @param outputErrors the errors of the current encoding
   */
  override fun backward(outputErrors: List<DenseNDArray>) = this.encoder.backward(outputErrors)

  /**
   * @param copy a boolean indicating whether the returned errors must be a copy or a reference
   *
   * @return the errors of the model parameters
   */
  override fun getInputErrors(copy: Boolean): NeuralProcessor.NoInputErrors = this.encoder.getInputErrors(copy)

  /**
   * @param copy whether to return by value or by reference (default true)
   *
   * @return the input errors of the last backward
   */
  override fun getParamsErrors(copy: Boolean): TokensEncoderParameters = this.encoder.getParamsErrors(copy)
}
