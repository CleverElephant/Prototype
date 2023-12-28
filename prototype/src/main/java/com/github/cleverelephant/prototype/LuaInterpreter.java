package com.github.cleverelephant.prototype;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Map.Entry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Executes lua prototype definitions.<br>
 * Prototypes consist of two parts:
 * <ul>
 * <li>Their full class name
 * <li>Their properties
 * </ul>
 * They are then written into the global table 'prototypes' using their name as key.<br>
 * See the example below.<br>
 *
 * <pre>
 * <code>
 * prototypes["prototypeName"] = {
 *     class = "com.example.ExamplePrototype",
 *     data = {
 *           exampleValue = 42,
 *           exampleList = { 1, 2, 3 }
 *     }
 * }
 * </code>
 * </pre>
 *
 * Then load the prototype using
 *
 * <pre>
 * <code>
 * Map<String, Object> context = ...;
 * LuaInterpreter interpreter = new LuaInterpreter(context);
 * interpreter.runScript("prototypes/prototypeName.lua");
 * interpreter.runScript("prototypes/anotherPrototype.lua");
 *
 * ObjectNode data = interpreter.computeData();
 * </code>
 * </pre>
 *
 * The result is an ObjectNode containing the data of both prototypes. Deserialization is done using a
 * {@link SerializationManager}.
 *
 * @author Benjamin Wied
 *
 * @see    SerializationManager
 * @see    <a href="https://github.com/luaj/luaj">luaj GitHub repository</a>
 */
public class LuaInterpreter
{
    private class CustomSearchPath extends VarArgFunction
    {
        public static final String FILE_SEP = "/";

        /* Copied and modified from org.luaj.vm2.lib.PackageLib.searchpath. */
        @Override
        public Varargs invoke(Varargs args)
        {
            String name = args.checkjstring(1);
            String path = args.checkjstring(2);
            String sep = args.optjstring(3, ".");
            String rep = args.optjstring(4, FILE_SEP);

            // check the path elements
            int e = -1;
            int n = path.length();
            StringBuilder sb = null;
            name = name.replace(sep.charAt(0), rep.charAt(0));
            while (e < n) {

                // find next template
                int b = e + 1;
                e = path.indexOf(';', b);
                if (e < 0)
                    e = path.length();
                String template = path.substring(b, e);

                // create filename
                int q = template.indexOf('?');
                String filename = template;
                if (q >= 0)
                    filename = template.substring(0, q) + name + template.substring(q + 1);

                // try opening the file
                InputStream is = globals.finder.findResource(filename);
                if (is != null) {
                    try {
                        is.close();
                    } catch (java.io.IOException ioe) {}
                    return valueOf(filename);
                }

                // report error
                if (sb == null)
                    sb = new StringBuilder();
                sb.append("\n\t" + filename);
            }
            return varargsOf(NIL, valueOf(sb.toString()));
        }
    }

    private Globals globals;

    /**
     * Constructs a new LuaInterpreter with the given context.
     *
     * @param context
     *                the context, available to lua via globals
     */
    public LuaInterpreter(Map<String, Object> context)
    {
        this(null, context);
    }

    /**
     * Constructs a new LuaInterpreter with the given context and basePath.
     *
     * @param basePath
     *                 to search for prototypes
     * @param context
     *                 the context, available to lua via globals
     */
    public LuaInterpreter(Path basePath, Map<String, Object> context)
    {
        globals = JsePlatform.standardGlobals();
        for (Entry<String, Object> entry : context.entrySet())
            globals.set(entry.getKey(), CoerceJavaToLua.coerce(entry.getValue()));
        globals.set("prototypes", new LuaTable());

        if (basePath != null)
            globals.get("package").set("path", LuaString.valueOf(basePath.toString()) + "/?.lua");
        globals.get("package").set("searchpath", new CustomSearchPath());
    }

    /**
     * Loads a lua script. This has the same effect as calling <code>require(file)</code> from lua.
     *
     * @param file
     *             the name of the lua script (with or without .lua extension), relative to basePath
     */
    public void runScript(String file)
    {
        if (file.endsWith(".lua"))
            file = file.substring(0, file.length() - 4);
        globals.load("require(\"" + file + "\")").call();
    }

    /**
     * Converts the lua prototype definitions into an object node.
     *
     * @return the object node
     */
    public ObjectNode computeData()
    {
        LuaTable prototypes = globals.get("prototypes").checktable();

        ObjectNode result = new ObjectNode(JsonNodeFactory.instance);

        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs data = prototypes.next(key);
            if (data == LuaValue.NIL)
                return result;
            key = data.arg(1);
            LuaTable prototype = data.arg(2).checktable();
            String c = prototype.get("class").checkjstring();
            JsonNode d = toJson(prototype.get("data").checktable());

            ObjectNode node = new ObjectNode(JsonNodeFactory.instance);
            node.set(c, d);
            result.set(key.checkjstring(), node);
        }
    }

    /**
     * Converts the given lua table into a json node.
     *
     * @param  table
     *               the lua table
     *
     * @return       the json node
     */
    public static JsonNode toJson(LuaTable table)
    {
        if (table.length() == 0)
            return tableToObject(table);
        return tableToArray(table);
    }

    private static JsonNode tableToArray(LuaTable table)
    {
        ArrayNode node = new ArrayNode(JsonNodeFactory.instance);

        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs data = table.next(key);
            if (data == LuaValue.NIL)
                return node;
            key = data.arg(1);
            LuaValue value = data.arg(2);

            if (value.istable())
                node.add(toJson(value.checktable()));
            else if (value.isboolean())
                node.add(value.checkboolean());
            else if (value.isint())
                node.add(value.checkint());
            else if (value.islong())
                node.add(value.checklong());
            else if (value.type() == LuaValue.TNUMBER)
                node.add(value.checkdouble());
            else if (value.isnil())
                node.addNull();
            else if (value.isstring())
                node.add(value.checkjstring());
            else if (value.isuserdata())
                node.addPOJO(value.touserdata());
            else
                throw new UnsupportedOperationException();
        }
    }

    private static JsonNode tableToObject(LuaTable table)
    {
        ObjectNode node = new ObjectNode(JsonNodeFactory.instance);

        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs data = table.next(key);
            if (data == LuaValue.NIL)
                return node;
            key = data.arg(1);
            LuaValue value = data.arg(2);

            if (value.istable())
                node.set(key.checkjstring(), toJson(value.checktable()));
            else if (value.isboolean())
                node.put(key.checkjstring(), value.checkboolean());
            else if (value.isint())
                node.put(key.checkjstring(), value.checkint());
            else if (value.islong())
                node.put(key.checkjstring(), value.checklong());
            else if (value.type() == LuaValue.TNUMBER)
                node.put(key.checkjstring(), value.checkdouble());
            else if (value.isnil())
                node.putNull(key.checkjstring());
            else if (value.isstring())
                node.put(key.checkjstring(), value.checkjstring());
            else if (value.isuserdata())
                node.putPOJO(key.checkjstring(), value.touserdata());
            else
                throw new UnsupportedOperationException();
        }
    }
}
