using System;
using Xunit;

namespace EventFinder.Api.Tests;

/// <summary>
/// The connection string must survive both shapes a hosting provider can hand
/// over: the Npgsql key/value format and a <c>postgresql://</c> URI. A URI fed
/// straight to Npgsql throws during startup and takes the whole deployment
/// down with it.
/// </summary>
public class NpgsqlConnectionStringTests
{
    [Fact]
    public void Key_value_strings_are_passed_through_unchanged()
    {
        const string value = "Host=db.internal;Port=5432;Database=eventfinder;Username=u;Password=p;SSL Mode=Require";

        Assert.Equal(value, NpgsqlConnectionString.Normalize(value));
    }

    [Fact]
    public void Postgres_uri_is_converted_to_the_key_value_format()
    {
        var normalized = NpgsqlConnectionString.Normalize(
            "postgresql://eventfinder:secret@dpg.example.com:5432/eventfinder_db");

        Assert.Contains("Host=dpg.example.com", normalized);
        Assert.Contains("Port=5432", normalized);
        Assert.Contains("Database=eventfinder_db", normalized);
        Assert.Contains("Username=eventfinder", normalized);
        Assert.Contains("Password=secret", normalized);
        Assert.Contains("SSL Mode=Require", normalized);
    }

    [Fact]
    public void Uri_without_an_explicit_port_falls_back_to_5432()
    {
        var normalized = NpgsqlConnectionString.Normalize(
            "postgresql://user:pw@db.internal/mydb");

        Assert.Contains("Port=5432", normalized);
        Assert.Contains("Database=mydb", normalized);
    }

    [Fact]
    public void Percent_encoded_credentials_are_decoded()
    {
        var normalized = NpgsqlConnectionString.Normalize(
            "postgresql://user%40mail.com:p%40ss%3Aword@db.internal:5432/mydb");

        Assert.Contains("Username=user@mail.com", normalized);
        Assert.Contains("Password=p@ss:word", normalized);
    }

    [Fact]
    public void Plain_postgres_scheme_is_accepted_too()
    {
        var normalized = NpgsqlConnectionString.Normalize(
            "postgres://user:pw@db.internal:5432/mydb");

        Assert.Contains("Host=db.internal", normalized);
    }
}
