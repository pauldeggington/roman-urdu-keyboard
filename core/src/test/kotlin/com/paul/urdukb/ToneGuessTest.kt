package com.paul.urdukb

import org.junit.Assert.assertEquals
import org.junit.Test

class ToneGuessTest {
    private fun g(text: String, base: Style = Style.CASUAL) = ToneGuess.guess(text, base)

    @Test fun plainSentencesKeepTheUsualTone() {
        assertEquals(Style.CASUAL, g("What time is the meeting?"))
        assertEquals(Style.FORMAL, g("What time is the meeting?", Style.FORMAL))
        assertEquals(Style.CASUAL, g(""))
        assertEquals(Style.CASUAL, g("I'll call you later, I'm in a meeting."))
    }

    @Test fun lovingWordsMeanAffectionate() {
        assertEquals(Style.AFFECTIONATE, g("Good morning, my love."))
        assertEquals(Style.AFFECTIONATE, g("I miss you so much ❤"))
        assertEquals(Style.AFFECTIONATE, g("Come home soon, sweetheart."))
    }

    @Test fun lovingAFoodIsNotAffection() {
        assertEquals(Style.CASUAL, g("I love pizza."))
        assertEquals(Style.CASUAL, g("I love this song, the music is great."))
    }

    @Test fun eldersMeanRespectful() {
        assertEquals(Style.RESPECTFUL, g("Please give my salaam to Mother."))
        assertEquals(Style.RESPECTFUL, g("Dear Sir, may I take leave tomorrow?"))
        assertEquals(Style.RESPECTFUL, g("Please make dua for me, Ammi."))
        assertEquals(Style.RESPECTFUL, g("How is your health, Grandfather?", Style.FORMAL))
    }

    @Test fun jokesMeanFunny() {
        assertEquals(Style.FUNNY, g("Haha you snore like a train, lol"))
        assertEquals(Style.FUNNY, g("Relax, I'm just kidding!"))
        // work talk is not a joke
        assertEquals(Style.CASUAL, g("My boss wants the report on Monday."))
    }

    @Test fun poetryNeedsMoreThanOneCue() {
        assertEquals(Style.POETIC, g("The moon is awake tonight and the silence is deep."))
        assertEquals(Style.POETIC, g("Write this as a ghazal: the night is long."))
        assertEquals(Style.CASUAL, g("I had a dream last night."))
    }

    @Test fun formalPeopleStayFormalUnlessItIsAnElderOrPoetry() {
        assertEquals(Style.FORMAL, g("Haha that was funny, lol", Style.FORMAL))
        assertEquals(Style.FORMAL, g("Good morning, my love.", Style.FORMAL))
        assertEquals(Style.POETIC, g("The moon and the silence of the night.", Style.FORMAL))
    }
}
