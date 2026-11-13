package app.financecopilot.parsing;

import app.financecopilot.domain.FileType;

import java.io.IOException;

public interface StatementParser {

    FileType fileType();

    /**
     * Parses the whole file. Unparseable transaction lines are reported in {@link ParseResult#failures()}
     * instead of failing the import; a file with no recognisable structure throws {@link StatementFormatException}.
     */
    ParseResult parse(byte[] content) throws IOException;
}
