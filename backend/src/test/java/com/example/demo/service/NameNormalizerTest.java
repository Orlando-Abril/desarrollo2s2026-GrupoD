package com.example.demo.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NameNormalizerTest {

    @Test
    void personIgnoresCaseDiacriticsPunctuationAndRepeatedSpaces() {
        assertThat(NameNormalizer.person("Martin Ødegaard")).isEqualTo("martin odegaard");
        assertThat(NameNormalizer.person("N'Golo Kanté")).isEqualTo("n golo kante");
        assertThat(NameNormalizer.person("Thomas  MÜLLER ")).isEqualTo("thomas muller");
        assertThat(NameNormalizer.person("Enzo Fernández")).isEqualTo(NameNormalizer.person("ENZO FERNANDEZ"));
        assertThat(NameNormalizer.person("Jean-Philippe Mateta")).isEqualTo("jean philippe mateta");
        assertThat(NameNormalizer.person("J. Timber")).isEqualTo("j timber");
        assertThat(NameNormalizer.person("Łukasz Skorupski")).isEqualTo("lukasz skorupski");
    }

    @Test
    void personOfNullIsEmpty() {
        assertThat(NameNormalizer.person(null)).isEmpty();
    }

    @Test
    void teamAlsoIgnoresInstitutionalTokens() {
        assertThat(NameNormalizer.team("Arsenal FC")).isEqualTo(NameNormalizer.team("Arsenal"));
        assertThat(NameNormalizer.team("Paris Saint-Germain FC")).isEqualTo("paris saint germain");
        assertThat(NameNormalizer.team("1. FC Köln")).isEqualTo("koln");
        assertThat(NameNormalizer.team("AFC Bournemouth")).isEqualTo("bournemouth");
        assertThat(NameNormalizer.team("Club Atlético de Madrid")).isEqualTo("atletico madrid");
    }

    /** Casos reales de team_not_found en la primera ejecución real (research V6). */
    @Test
    void teamIgnoresInstitutionalPrefixesAndSuffixesSeenInTheRealRun() {
        assertThat(NameNormalizer.team("US Sassuolo Calcio")).isEqualTo(NameNormalizer.team("Sassuolo"));
        assertThat(NameNormalizer.team("SS Lazio")).isEqualTo(NameNormalizer.team("Lazio"));
        assertThat(NameNormalizer.team("AJ Auxerre")).isEqualTo(NameNormalizer.team("Auxerre"));
        assertThat(NameNormalizer.team("ES Troyes AC")).isEqualTo(NameNormalizer.team("Troyes"));
        assertThat(NameNormalizer.team("CA Osasuna")).isEqualTo(NameNormalizer.team("Osasuna"));
        assertThat(NameNormalizer.team("Angers SCO")).isEqualTo(NameNormalizer.team("Angers"));
        assertThat(NameNormalizer.team("ACF Fiorentina")).isEqualTo(NameNormalizer.team("Fiorentina"));
        assertThat(NameNormalizer.team("Atalanta BC")).isEqualTo(NameNormalizer.team("Atalanta"));
        assertThat(NameNormalizer.team("Genoa CFC")).isEqualTo(NameNormalizer.team("Genoa"));
        assertThat(NameNormalizer.team("Real Betis Balompié")).isEqualTo(NameNormalizer.team("Real Betis"));
        assertThat(NameNormalizer.team("Real Sociedad de Fútbol")).isEqualTo(NameNormalizer.team("Real Sociedad"));
    }

    @Test
    void teamIgnoresNumericTokensSuchAsFoundationYears() {
        assertThat(NameNormalizer.team("Como 1907")).isEqualTo(NameNormalizer.team("Como"));
        assertThat(NameNormalizer.team("Bologna FC 1909")).isEqualTo(NameNormalizer.team("Bologna"));
        assertThat(NameNormalizer.team("TSG 1899 Hoffenheim")).isEqualTo(NameNormalizer.team("Hoffenheim"));
        assertThat(NameNormalizer.team("1. FSV Mainz 05")).isEqualTo(NameNormalizer.team("Mainz 05"));
        assertThat(NameNormalizer.team("SC Paderborn 07")).isEqualTo(NameNormalizer.team("Paderborn"));
        assertThat(NameNormalizer.team("Bayer 04 Leverkusen")).isEqualTo(NameNormalizer.team("Bayer Leverkusen"));
    }

    @Test
    void teamStillDoesNotResolveDifferentNamesWithoutAliases() {
        assertThat(NameNormalizer.team("Tottenham Hotspur FC")).isNotEqualTo(NameNormalizer.team("Tottenham"));
        assertThat(NameNormalizer.team("FC Bayern München")).isNotEqualTo(NameNormalizer.team("Bayern Munich"));
        assertThat(NameNormalizer.team("FC Internazionale Milano")).isNotEqualTo(NameNormalizer.team("Inter"));
    }

    @Test
    void teamOnlyRemovesWholeTokens() {
        assertThat(NameNormalizer.team("Monaco")).isEqualTo("monaco");
        assertThat(NameNormalizer.team("AS Monaco FC")).isEqualTo("monaco");
        assertThat(NameNormalizer.team("Cádiz CF")).isEqualTo("cadiz");
    }

    @Test
    void doesNotApproximateDifferentNames() {
        assertThat(NameNormalizer.team("Man Utd")).isNotEqualTo(NameNormalizer.team("Manchester United FC"));
    }
}
