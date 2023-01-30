/**
 * MIT License
 *
 * Copyright (c) 2023 Benjamin Wied
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.github.cleverelephant.prototype.parser;

import com.github.cleverelephant.prototype.parser.antlr.PrototypeLexer;
import com.github.cleverelephant.prototype.parser.antlr.PrototypeParser;

import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

/**
 * Deserializes prototype definitions unsint ANTRL4.
 *
 * @author Benjamin Wied
 */
public final class DefinitionDeserializer
{
    private DefinitionDeserializer()
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Deserializes a prototype definition from the given input.
     *
     * @param  input
     *               definition data
     *
     * @return       action deserialized
     */
    public static PrototypeDefinition deserializeDefinition(String input)
    {
        PrototypeLexer lexer = new PrototypeLexer(CharStreams.fromString(input));

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PrototypeParser parser = new PrototypeParser(tokens);
        parser.setErrorHandler(new BailErrorStrategy());

        return new ActionGeneratingVisitor().visitPrototype(parser.prototype());
    }
}