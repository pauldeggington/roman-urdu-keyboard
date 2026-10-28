package com.paul.urdukb.ime

import java.io.File
import org.junit.Test

/** Prints how many everyday typos the automatic correction fixes (no assertions; it is a measuring stick). */
class AutocorrectCoverageTest {
    private val typos = """teh the,adn and,taht that,wiht with,waht what,jsut just,hte the,yuo you,thier their,recieve receive,definately definitely,becuase because,seperate separate,occured occurred,untill until,tommorow tomorrow,wich which,beleive believe,freind friend,realy really,realy really,probly probably,im i'm,dont don't,cant can't,wont won't,didnt didn't,doesnt doesn't,isnt isn't,wasnt wasn't,couldnt couldn't,shouldnt shouldn't,wouldnt wouldn't,ive i've,youre you're,theyre they're,thats that's,whats what's,hows how's,lets let's,helo hello,hellp hello,thnks thanks,thanx thanks,thankyou thank,pleas please,plese please,sory sorry,sorrry sorry,gud good,goood good,morining morning,mornign morning,evening evening,tonite tonight,tomorow tomorrow,wanna wanna,gonna gonna,alot a lot,abou about,aboutt about,becasue because,beacuse because,wnat want,whta what,knwo know,konw know,thnk think,thikn think,somthing something,somethng something,anyting anything,evrything everything,everthing everything,peple people,poeple people,diffrent different,differnt different,interseting interesting,intresting interesting,beutiful beautiful,beautifull beautiful,accomodate accommodate,adress address,adn and,toghether together,togather together,calender calendar,cheif chief,comming coming,commited committed,concious conscious,enviroment environment,existance existence,foriegn foreign,goverment government,happend happened,immediatly immediately,independant independent,knowlege knowledge,libary library,maintainance maintenance,neccessary necessary,noticable noticeable,occassion occasion,paralel parallel,persue pursue,posible possible,prefered preferred,reccomend recommend,refered referred,relevent relevant,religous religious,remeber remember,rember remember,sucess success,succesful successful,suprise surprise,tendancy tendency,truely truly,unfortunatly unfortunately,wierd weird,writting writing,youself yourself"""
    @Test
    fun coverage() {
        val s = WordSuggester(WordSuggester.readWords(File("src/main/assets/dict/en.tsv").readLines().asSequence()))
        val miss = ArrayList<String>()
        var ok = 0; var total = 0; var wrong = 0
        for (pair in typos.split(',').map { it.trim() }.distinct()) {
            val (t, want) = pair.split(' ')
            if (t == want || t == "wanna" || t == "gonna" || t == "evening") continue
            total++
            var why = ""
            s.trace = { why = it }
            val got = s.autocorrect(t)
            if (got != want && t in setOf("helo", "wich", "sory", "pleas", "rember", "hellp", "wnat", "lets", "probly", "alot")) println("AUTOCORRECT why: $why -> $got")
            if (got == want) ok++ else if (got == null) miss += t else { wrong++; miss += "$t->$got(want $want)" }
        }
        println("AUTOCORRECT coverage: fixed $ok of $total, wrong $wrong")
        println("AUTOCORRECT missed: " + miss.joinToString(" "))
    }
}
