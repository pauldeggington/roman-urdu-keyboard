package com.paul.urdukb

/**
 * The tone matrix, as example banks: for each extra tone, "English | Roman Urdu" pairs grouped by the situation they
 * belong to (a "# heading" line starts a group; the model never sees the headings, they are for people reading the list).
 * For each message the app shows the model the closest few pairs, so the more situations a bank covers, the better a
 * sentence in that situation is matched. Written for a male speaker like the other banks; Speaker.adapt handles the rest.
 */
object ToneBank {

    val POETIC = """# longing
I miss you more with every passing night. | Har guzarti raat ke saath tumhari yaad aur gehri ho jati hai.
Your memory visits me like the evening breeze. | Tumhari yaad shaam ki hawa ki tarah mujh tak chali aati hai.
I still wait for you at the end of every day. | Har din ke aakhir mein main aaj bhi tumhara intezar karta hoon.
# love: poetic
You are the dream my eyes never want to wake from. | Tum woh khwab ho jis se meri aankhein kabhi jagna nahi chahtin.
My heart has found its home in you. | Mere dil ko tum mein apna ghar mil gaya hai.
Even the moon feels incomplete without you. | Tumharay bina chand bhi adhoora lagta hai.
# night
The moon is awake tonight, and so am I. | Aaj chand bhi jaag raha hai, aur main bhi.
The night is quiet, but my heart is not. | Raat khamosh hai, magar mera dil nahi.
# hope
Every sunrise is a promise that life begins again. | Har subah ek wada hai ke zindagi phir se shuru ho sakti hai.
Even the longest night ends in dawn. | Sab se lambi raat bhi aakhir subah par khatam hoti hai.
Time heals the wounds that words cannot. | Jo zakhm lafz nahi bhar saktay, unhein waqt bhar deta hai.
# sorrow
Some silences say more than a thousand words. | Kuch khamoshiyan hazaar lafzon se zyada kehti hain.
Tears are the words the heart could not speak. | Aansoo woh lafz hain jo dil keh na saka.
# friendship
A true friend is a lamp in the darkest hour. | Sacha dost andheray waqt ka chiragh hota hai.
Thank you for being my light. | Meri roshni bannay ka shukriya.
# journey
Life is a journey, not a destination. | Zindagi ek safar hai, manzil nahi.
Wherever you go, my prayers go with you. | Tum jahan bhi jao, meri duayein tumharay saath jayen gi.
# love: good morning and good night
Good morning, my love. | Subah bakhair, meri jaan.
Good night, sweet dreams. | Shab bakhair, meethay khwab.
Sleep well, I'll see you in my dreams. | Aram se sona, khwabon mein miltay hain.
# love: words of love
I love you more than words can say. | Mujhe tum se itna pyaar hai ke lafz kam par jatay hain.
You mean everything to me. | Tum meray liye sab kuch ho.
You make me so happy. | Tum mujhe bohat khush rakhtay ho.
# love: missing someone
I can't wait to see you. | Mujh se tumhein dekhnay ka intezar nahi ho raha.
Come home soon, I miss you. | Jaldi ghar aa jao, mujhe tumhari bohat yaad aa rahi hai.
# love: care
Did you eat? Please don't skip meals. | Khana khaya? Please khana skip mat kiya karo.
Take care of yourself for me. | Meray liye apna khayal rakhna.
Are you feeling better, sweetheart? | Ab tabiyat behtar hai, meri jaan?
# love: comfort
I'm always here for you. | Main hamesha tumharay saath hoon.
Don't cry, everything will be okay. | Ro mat, sab theek ho jayega.
# love: appreciation
You're the best thing that ever happened to me. | Tum meri zindagi ki sab se achi cheez ho.
Thank you for always understanding me. | Mujhe hamesha samajhnay ka shukriya.
I'm so proud of you. | Mujhe tum par bohat fakhar hai.
# plain things stay plain
What time is the meeting? | Meeting kitnay baje hai?
I'll be there at five. | Main paanch baje wahan hoon ga."""

    val FUNNY = """# banter
You're late again, what a surprise! | Phir late? Hairat hai, bilkul expect nahi tha!
Did you forget I exist? | Kya tum bhool gaye ke main bhi zinda hoon?
Wow, you actually replied. Miracles do happen. | Wah, tum ne reply kar diya. Mojizay waqai hotay hain.
You think you're funny? Well, you are. | Tumhein lagta hai tum mazaaqiya ho? Chalo, ho bhi.
# food
Don't touch my food, I'm warning you. | Mere khanay ko haath mat lagana, main warning de raha hoon.
I only came for the biryani. | Main to sirf biryani ke liye aaya hoon.
I'm on a diet. I'll start tomorrow. | Main diet par hoon. Kal se shuru karoon ga.
# lazy
I'm not lazy, I'm in energy-saving mode. | Main sust nahi hoon, bas energy-saving mode mein hoon.
Five more minutes, then five more. | Bas paanch minute aur, phir paanch minute aur.
# teasing
Relax, I'm just joking. | Araam se, main to mazaaq kar raha hoon.
Don't be so dramatic. | Itna drama mat karo.
You snore like a train! | Tum to train ki tarah kharratay letay ho!
# work
My boss thinks I'm working. Don't tell him. | Mere boss ko lagta hai main kaam kar raha hoon. Usay mat batana.
It's Monday again. Send help. | Phir peer aa gaya. Madad bhejo.
# money
My wallet is crying right now. | Mera wallet is waqt ro raha hai.
# self
I'm not short, I'm fun-sized. | Main chhota nahi hoon, main fun-size hoon."""

    val RESPECTFUL = """# greeting
Hello, how are you, Uncle? | Assalam o alaikum Uncle, aap kaisay hain?
How is your health, Grandfather? | Dada jaan, aap ki sehat kaisi hai?
Please give my salaam to Mother. | Ammi jaan ko meri taraf se salam kahiye ga.
# request
Please make dua for me. | Meray liye dua kijiye ga.
May I come in, sir? | Kya main andar aa sakta hoon, janab?
Could you please guide me? | Kya aap meri rehnumai farma saktay hain?
Please forgive my mistake. | Meri ghalti maaf kijiye ga.
# thanks
Thank you for your kindness and guidance. | Aap ki meherbani aur rehnumai ka bohat shukriya.
We are grateful for your blessings. | Hum aap ki duaon ke shukar guzaar hain.
# care
Please take care of your health, Father. | Abbu jaan, apni sehat ka khayal rakhiye ga.
Have you taken your medicine? | Kya aap ne apni dawai li?
Please rest, you must be tired. | Aap aaraam farmayen, aap thak gaye hon gay.
# invitation
You are most welcome in our home. | Aap ka hamaray ghar mein dil se istaqbal hai.
Please do visit us soon. | Kabhi tashreef layen.
# occasions
Eid Mubarak to you and your family. | Aap aur aap ke ghar walon ko Eid Mubarak.
May Allah give you a long and healthy life. | Allah aap ko lambi aur sehatmand zindagi ata farmaye.
# permission
With your permission, I will leave now. | Aap ki ijazat ho to main ab chalta hoon."""

    /** The bank for each extra tone. */
    fun forStyle(style: Style): String? = when (style) {
        Style.POETIC -> POETIC
        Style.FUNNY -> FUNNY
        Style.RESPECTFUL -> RESPECTFUL
        else -> null
    }
}
