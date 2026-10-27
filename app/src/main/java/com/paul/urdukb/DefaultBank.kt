package com.paul.urdukb

/**
 * Starter content: a bank of example pairs per style, and the spelling glossary.
 * Roman Urdu output is written for a male speaker ("main ... raha hoon"); Speaker.adapt rewrites it for
 * a female or neutral speaker. "ay" spellings, plain spoken Urdu.
 * The keyboard shows the model only the closest few for each message; edit or extend them in the app.
 */
object DefaultBank {

    /**
     * Questions asked TO the listener ("How are you?") were sometimes answered as if they were about the speaker
     * ("Main theek hoon"). These pairs show the difference. They are added once to banks saved before.
     */
    val ADDED_QUESTIONS = mapOf(
        "casual" to """How have you been? | Tumhara kya haal hai?
How are you doing today? | Aaj tum kaisay ho?
Are you okay? | Tum theek ho?
Are you well? | Kya tum theek ho?
How is your family? | Tumharay ghar walay kaisay hain?
How are you feeling? | Tabiyat kaisi hai?
Hope you are well. | Umeed hai tum theek ho.""",
        "formal" to """How have you been? | Aap ka kya haal hai?
How are you doing today? | Aaj aap kaisay hain?
Are you okay? | Kya aap theek hain?
Are you well? | Kya aap theek hain?
How is your family? | Aap ke ghar walay kaisay hain?
How are you feeling? | Aap ki tabiyat kaisi hai?""",
        "english" to """Main theek hoon. | I'm fine.
Tum theek ho? | Are you okay?
Aap kaisay hain? | How are you?
Tumhara kya haal hai? | How have you been?
Main thak gaya hoon. | I'm tired.""",
    )

    val CASUAL = """How are you? | Tum kaisay ho?
What are you doing? | Kya kar rahay ho?
What do you want to eat? | Kya khana chahtay ho?
Are you coming tonight? | Kya tum aaj raat aa rahay ho?
Where are you? I've been waiting for an hour. | Tum kahan ho? Main ek ghantay se wait kar raha hoon.
Thanks, that really helped. | Thanks, is se kaafi madad mili.
I'll call you later, I'm in a meeting. | Main baad mein call karta hoon, abhi meeting mein hoon.
I was going to tell you. | Main tumhe batanay wala tha.
I love you. | Tumse pyaar hai.
I miss you. | Tumhari yaad aa rahi hai.
Don't worry, everything will be fine. | Fikar mat karo, sab theek ho jayega.
Did you eat? | Khana khaya?
I'm hungry. | Bhook lagi hai.
I'm tired. | Main thak gaya hoon.
Can you send me the address? | Mujhe address bhej saktay ho?
Let me know when you reach. | Pohanch jao to bata dena.
I'll be there in ten minutes. | Main das minute mein pohanch raha hoon.
Why didn't you reply? | Jawab kyun nahi diya?
Sorry, I forgot. | Sorry, main bhool gaya tha.
What time should we meet? | Kitnay baje milna hai?
Is this your phone? | Kya yeh tumhara phone hai?
Happy birthday! | Birthday mubarak!
I don't understand. | Samajh nahi aayi.
I'm on my way. | Main raaste mein hoon.
Please call me back. | Mujhe wapas call kar lo.
It was a good day. | Din achha guzra.
Let's go for a walk. | Chalo walk par chaltay hain.
I want to talk to you. | Tumse baat karni hai.
Have you finished work? | Kaam khatam ho gaya?
Take care. | Apna khayal rakhna.
What happened? | Kya hua?
Are you free tomorrow? | Kya tum kal free ho?
I'll text you when I get home. | Main ghar pohanch kar message karta hoon.
What's the plan for dinner? | Raat ke khanay ka kya plan hai?
Do you need anything from the market? | Bazaar se kuch chahiye?
I'm leaving now. | Main abhi nikal raha hoon.
Can we talk for a minute? | Kya hum ek minute baat kar saktay hain?
That sounds good to me. | Mujhe theek lag raha hai.
I am well. | Main theek hoon.
I'm doing well, how about you? | Main theek hoon, tum sunao?
I am working right now. | Main abhi kaam kar raha hoon.
I'm making dinner. | Main raat ka khana bana raha hoon.
I can't come today. | Main aaj nahi aa sakta.
I am at home. | Main ghar par hoon.
I already ate. | Main ne khana kha liya hai.""" + "\n" + ADDED_QUESTIONS.getValue("casual")

    val FORMAL = """How are you? | Aap kaisay hain?
What would you like to eat? | Aap kya khana chahtay hain?
Are you coming tonight? | Kya aap aaj raat aa rahay hain?
Where are you? I've been waiting for an hour. | Aap kahan hain? Main ek ghantay se aap ka intezar kar raha hoon.
Thank you very much, that really helped. | Bohat shukriya, is se kaafi madad mili.
I'll call you later, I'm in a meeting. | Main aap ko thori der mein call karta hoon, abhi meeting mein hoon.
Please let me know when you are free. | Jab aap free hon to bata dijiye ga.
Could you please send me the address? | Meherbani karke address bhej dijiye.
I apologise for the delay. | Main der ke liye maazrat chahta hoon.
Is this your phone? | Kya yeh aap ka phone hai?
What time would be convenient for you? | Aap ke liye kaun sa waqt munasib rahay ga?
I will send you the document today. | Main aaj hi aap ko document bhej deta hoon.
Thank you for your time. | Aap ke waqt ka shukriya.
Please sit down. | Baith jaiye.
May I ask you something? | Kya main aap se kuch pooch sakta hoon?
I did not understand, could you repeat that? | Mujhe samajh nahi aayi, kya aap dobara bata saktay hain?
Happy birthday to you! | Aap ko birthday mubarak ho!
Please take care of yourself. | Apna khayal rakhiye ga.
Welcome. | Khush aamdeed.
We will reach in ten minutes. | Hum das minute mein pohanch jayen gay.
Please accept my apologies. | Meri maazrat qubool kijiye.
How may I help you? | Main aap ki kya madad kar sakta hoon?
Have you had your tea? | Aap ne chai pi li?
Please give my regards to your family. | Apne ghar walon ko meri taraf se salam kahiye ga.
Are you free tomorrow? | Kya aap kal free hain?
I will get back to you soon. | Main jald hi aap se rabta karta hoon.
Please call me when you arrive. | Pohanch kar mujhe call kar dijiye ga.
Thank you for your help. | Aap ki madad ka shukriya.
Could we meet on Monday? | Kya hum peer ko mil saktay hain?
I hope you are well. | Umeed hai aap khairiyat se hon gay.
I am fine, thank you. | Main theek hoon, shukriya.
I am working on it. | Main is par kaam kar raha hoon.""" + "\n" + ADDED_QUESTIONS.getValue("formal")

    val TO_ENGLISH = """Tum kaisay ho? | How are you?
Tum aaj kya karna chahtay ho? | What do you want to do today?
Kya tum pagal ho? | Are you crazy?
Baad mein call kartay hain, abhi meeting mein hain. | I'll call you later, I'm in a meeting right now.
Aap kahan hain? Ek ghantay se intezar kar rahay hain. | Where are you? I've been waiting for an hour.
Heater on hai? | Is the heater on?
Bohat shukriya, is se kaafi madad mili. | Thank you so much, that really helped.
Tumse pyaar hai. | I love you.
Tumhari yaad aa rahi hai. | I miss you.
Fikar mat karo, sab theek ho jayega. | Don't worry, everything will be fine.
Khana khaya? | Did you eat?
Main thak gaya hoon. | I'm tired.
Thak gaye hain. | I'm tired.
Kitnay baje milna hai? | What time should we meet?
Mujhe samajh nahi aayi. | I don't understand.
Main raaste mein hoon. | I'm on my way.
Main theek hoon. | I'm fine.
Kya yeh tumhara phone hai? | Is this your phone?
Jawab kyun nahi diya? | Why didn't you reply?
Bhool gaye thay, sorry. | I forgot, sorry.
Chalo walk par chaltay hain. | Let's go for a walk.
Apna khayal rakhna. | Take care of yourself.
Mujhe wapas call kar lo. | Please call me back.
Abhi tak kaam khatam nahi hua. | I haven't finished work yet.
Ghar pohanch kar bata dena. | Let me know once you get home.
Yeh kaisay kaam karta hai? | How does this work?
Mujhe bohat bhook lagi hai. | I'm really hungry.
Kal subah milte hain. | See you tomorrow morning.
kya kr rhe ho | What are you doing?
mujhe nhi pta | I don't know.
tm kb aa rhe ho | When are you coming?
kal ka kia plan hai | What's the plan for tomorrow?
acha thk hai | Okay, that's fine.
yar bht thak gya hn | I'm really tired.
abhi busy hn baad mein baat krta hn | I'm busy right now, I'll talk later.
ghar pohanch gye | I reached home.""" + "\n" + ADDED_QUESTIONS.getValue("english")

    /** word -> preferred spelling. Fixes Hindi-sounding spellings and applies the "ay" rule. */
    val GLOSSARY = """# one per line: word -> the spelling to use instead
bada -> barha
badi -> barhi
bade -> barhay
kaise -> kaisay
chahte -> chahtay
karte -> kartay
rahe -> rahay
jaate -> jaatay
jate -> jatay
hote -> hotay
dekhte -> dekhtay
sakte -> saktay
bolte -> boltay
milte -> miltay
kahte -> kahtay
ghante -> ghantay
pehle -> pehlay
mere -> meray
tumhare -> tumharay
aapke -> aapkay
humare -> humaray
uske -> uskay
iske -> iskay
dhanyawad -> shukriya
kripya -> meherbani
samay -> waqt
vichar -> khayal"""

    /** Lines of the earlier default bank that read as "you"; replaced in banks saved before. */
    val REPLACED = mapOf(
        "Where are you? I've been waiting for an hour. | Tum kahan ho? Ek ghantay se wait kar rahay hain." to "Where are you? I've been waiting for an hour. | Tum kahan ho? Main ek ghantay se wait kar raha hoon.",
        "I'll call you later, I'm in a meeting. | Baad mein call kartay hain, abhi meeting mein hain." to "I'll call you later, I'm in a meeting. | Main baad mein call karta hoon, abhi meeting mein hoon.",
        "I was going to tell you. | Tumhe batanay walay thay." to "I was going to tell you. | Main tumhe batanay wala tha.",
        "I'm tired. | Thak gaye hain." to "I'm tired. | Main thak gaya hoon.",
        "I'll be there in ten minutes. | Das minute mein pohanch rahay hain." to "I'll be there in ten minutes. | Main das minute mein pohanch raha hoon.",
        "Sorry, I forgot. | Sorry, bhool gaye thay." to "Sorry, I forgot. | Sorry, main bhool gaya tha.",
        "I'm on my way. | Raaste mein hain." to "I'm on my way. | Main raaste mein hoon.",
        "I'll text you when I get home. | Ghar pohanch kar message kartay hain." to "I'll text you when I get home. | Main ghar pohanch kar message karta hoon.",
        "I'm leaving now. | Abhi nikal rahay hain." to "I'm leaving now. | Main abhi nikal raha hoon.",
        "Where are you? I've been waiting for an hour. | Aap kahan hain? Ek ghantay se aap ka intezar kar rahay hain." to "Where are you? I've been waiting for an hour. | Aap kahan hain? Main ek ghantay se aap ka intezar kar raha hoon.",
        "I'll call you later, I'm in a meeting. | Aap ko thori der mein call kartay hain, abhi meeting mein hain." to "I'll call you later, I'm in a meeting. | Main aap ko thori der mein call karta hoon, abhi meeting mein hoon.",
        "I apologise for the delay. | Der hone par maazrat chahtay hain." to "I apologise for the delay. | Main der ke liye maazrat chahta hoon.",
        "I will send you the document today. | Aaj hi aap ko document bhej detay hain." to "I will send you the document today. | Main aaj hi aap ko document bhej deta hoon.",
        "May I ask you something? | Kya hum aap se kuch pooch saktay hain?" to "May I ask you something? | Kya main aap se kuch pooch sakta hoon?",
        "How may I help you? | Hum aap ki kya madad kar saktay hain?" to "How may I help you? | Main aap ki kya madad kar sakta hoon?",
        "I will get back to you soon. | Hum jald hi aap se rabta kartay hain." to "I will get back to you soon. | Main jald hi aap se rabta karta hoon."
    )
}
