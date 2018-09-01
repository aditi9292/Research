package org.apache.james.mime4j.field.address.parser;

public class Token
{
  public int beginColumn;
  public int beginLine;
  public int endColumn;
  public int endLine;
  public String image;
  public int kind;
  public Token next;
  public Token specialToken;

  public Token()
  {
  }

  public static final Token newToken(int paramInt)
  {
    return new Token();
  }

  public String toString()
  {
    return this.image;
  }
}

/* Location:
 * Qualified Name:     org.apache.james.mime4j.field.address.parser.Token
 * Java Class Version: 6 (50.0)
 * JD-Core Version:    0.6.1-SNAPSHOT
 */