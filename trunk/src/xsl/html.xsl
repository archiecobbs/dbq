<?xml version="1.0" encoding="ISO-8859-1"?>

<!-- $Id$ -->
<xsl:transform xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

    <xsl:output method="html" indent="yes" encoding="utf-8" media-type="text/html"
      doctype-system="http://www.w3.org/TR/html4/strict.dtd"
      doctype-public="-//W3C//DTD HTML 4.01//EN"/>

    <xsl:template match="/result-set">
        <html>
            <head>
                <title>DBQ Results</title>
                <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
                <style type="text/css">

                    body {
                        font-size: small;
                    }

                    div.query {
                        padding: 10px 10px 10px 10px;
                        width: 95%;
                        background-color: #ccf;
                        font-style: italic;
                    }

                    div.data table {
                        margin-top: 10px;
                        margin-left: auto;
                        margin-right: auto;
                        padding: 0px;
                        border-collapse: collapse;
                    }

                    div.data table tr.table-header {
                        background-color: #fcc;
                    }

                    div.data table tr.even {
                        background-color: #fee;
                    }

                    div.data table td {
                        border: 1px solid #000000;
                    }

                    div.data table tr.table-header td {
                        font-weight: bold;
                    }

                </style>
            </head>
            <body>
            <xsl:apply-templates/>
            </body>
        </html>
    </xsl:template>

    <xsl:template match="/result-set/query">
        <div class="query">
            <xsl:value-of select="."/>
        </div>
    </xsl:template>

    <xsl:template match="/result-set/data">
        <xsl:variable name="columns" select="../columns/column[@index = ../../data/row/column[string-length(.) &gt; 0]/@index]"/>
        <div class="data">
            <table>
                <xsl:call-template name="column.headers">
                    <xsl:with-param name="columns" select="$columns"/>
                </xsl:call-template>
                <xsl:for-each select="row">
                    <xsl:variable name="row" select="."/>
                    <xsl:variable name="evenodd">
                        <xsl:choose>
                            <xsl:when test="position() mod 2 = 0">even</xsl:when>
                            <xsl:otherwise>odd</xsl:otherwise>
                        </xsl:choose>
                    </xsl:variable>
                    <xsl:if test="position() mod 25 = 0">
                        <xsl:call-template name="column.headers">
                            <xsl:with-param name="columns" select="$columns"/>
                        </xsl:call-template>
                    </xsl:if>
                    <tr class="table-row {$evenodd}">
                        <xsl:for-each select="$columns">
                            <!-- <xsl:sort select="@name" data-type="text"/> -->
                            <xsl:variable name="index" select="@index"/>
                            <td>
                                <xsl:variable name="cell" select="$row/column[@index = $index]"/>
                                <xsl:value-of select="$cell"/>
                            </td>
                        </xsl:for-each>
                    </tr>
                </xsl:for-each>
            </table>
        </div>
        <xsl:variable name="not.shown" select="../columns/column[not(@index = ../../data/row/column[string-length(.) &gt; 0]/@index)]"/>
        <xsl:if test="$not.shown">
            <div class="columns-not-shown">
                <p>The following <xsl:value-of select="count($not.shown)"/> empty columns are not shown:</p>
                <ul>
                    <xsl:for-each select="$not.shown">
                        <xsl:sort select="@name" data-type="text"/>
                        <li><xsl:value-of select="@name"/></li>
                    </xsl:for-each>
                </ul>
            </div>
        </xsl:if>
    </xsl:template>

    <xsl:template name="column.headers">
        <xsl:param name="columns"/>
        <tr class="table-header">
            <xsl:for-each select="$columns">
                <!-- <xsl:sort select="@name" data-type="text"/> -->
                <xsl:variable name="column" select="."/>
                <td>
                    <xsl:value-of select="$column/@name"/>
                </td>
            </xsl:for-each>
        </tr>
    </xsl:template>

    <xsl:template match="node()|@*"/>

</xsl:transform>
