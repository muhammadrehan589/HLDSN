package com.example.hldsn.firstaid;

import com.example.hldsn.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class FirstAidTipsRepository {

    private FirstAidTipsRepository() {
    }

    public static List<FirstAidTip> getTips() {
        List<FirstAidTip> tips = new ArrayList<>();

        tips.add(new FirstAidTip(1, "CPR", "Life Threatening", "Critical", "heart-pulse",
                "For a person who is not breathing normally.",
                Arrays.asList("cpr", "not breathing", "heart stopped", "unconscious"),
                Arrays.asList(
                        "Check if the person is responsive and breathing.",
                        "Call Rescue 1122 immediately.",
                        "Place the person flat on their back on a firm surface.",
                        "Put both hands in the center of the chest.",
                        "Push hard and fast at 100-120 compressions per minute.",
                        "Continue until help arrives or the person starts breathing."
                ),
                Arrays.asList(
                        "Do not delay calling emergency help.",
                        "Do not stop CPR unless the person breathes or help arrives.",
                        "Do not press on the stomach or ribs."
                )));

        tips.add(new FirstAidTip(2, "Choking", "Life Threatening", "Critical", "user-x",
                "For a person who cannot breathe, cough, or speak.",
                Arrays.asList("choking", "food stuck", "cannot breathe", "throat"),
                Arrays.asList(
                        "Ask: Are you choking?",
                        "If the person cannot speak, call for help.",
                        "Give 5 back blows between the shoulder blades.",
                        "If still choking, give abdominal thrusts.",
                        "Repeat until the object comes out or help arrives.",
                        "If the person becomes unconscious, start CPR."
                ),
                Arrays.asList(
                        "Do not put fingers blindly into the mouth.",
                        "Do not give water while the person is choking.",
                        "Do not slap the back if the person can cough strongly."
                )));

        tips.add(new FirstAidTip(3, "Heavy Bleeding", "Injury", "Critical", "droplet",
                "For serious bleeding from a wound.",
                Arrays.asList("bleeding", "blood", "cut", "wound", "injury"),
                Arrays.asList(
                        "Apply firm pressure with a clean cloth or bandage.",
                        "Keep pressure on the wound.",
                        "Raise the injured part if possible.",
                        "Call Rescue 1122 if bleeding is heavy.",
                        "Keep the person calm and lying down.",
                        "Add more cloth if blood soaks through; do not remove the first cloth."
                ),
                Arrays.asList(
                        "Do not remove deep objects stuck in the wound.",
                        "Do not wash a heavily bleeding wound first.",
                        "Do not remove soaked cloth; add another layer."
                )));

        tips.add(new FirstAidTip(4, "Burns", "Burns", "Serious", "flame",
                "For skin burns from heat, fire, or hot liquid.",
                Arrays.asList("burn", "fire", "hot water", "skin burn"),
                Arrays.asList(
                        "Move the person away from the source of burn.",
                        "Cool the burn under clean running water for at least 20 minutes if possible.",
                        "Remove tight items like rings or watches near the burn.",
                        "Cover the burn with clean cloth or sterile dressing.",
                        "Call emergency help for large, deep, face, hand, or chest burns."
                ),
                Arrays.asList(
                        "Do not apply toothpaste, oil, butter, or cream.",
                        "Do not burst blisters.",
                        "Do not use ice directly on the burn."
                )));

        tips.add(new FirstAidTip(5, "Heart Attack", "Life Threatening", "Critical", "heart",
                "For chest pain, pressure, sweating, or breathing difficulty.",
                Arrays.asList("heart attack", "chest pain", "sweating", "breathing problem"),
                Arrays.asList(
                        "Call Rescue 1122 immediately.",
                        "Help the person sit comfortably and rest.",
                        "Loosen tight clothing.",
                        "Keep the person calm.",
                        "If the person becomes unconscious and stops breathing normally, start CPR."
                ),
                Arrays.asList(
                        "Do not allow the person to walk around.",
                        "Do not give food or drink.",
                        "Do not delay emergency help."
                )));

        tips.add(new FirstAidTip(6, "Unconscious Person", "Life Threatening", "Critical", "person-standing",
                "For a person who is not responding.",
                Arrays.asList("unconscious", "faint", "not responding", "passed out"),
                Arrays.asList(
                        "Check if the area is safe.",
                        "Check if the person is breathing.",
                        "Call Rescue 1122.",
                        "If breathing, place the person in recovery position.",
                        "Keep the airway open.",
                        "If not breathing normally, start CPR."
                ),
                Arrays.asList(
                        "Do not give food or water.",
                        "Do not shake the person hard.",
                        "Do not leave the person alone."
                )));

        tips.add(new FirstAidTip(7, "Fracture / Broken Bone", "Injury", "Serious", "bone",
                "For suspected broken bone or serious injury.",
                Arrays.asList("fracture", "broken bone", "bone", "injury", "swelling"),
                Arrays.asList(
                        "Keep the injured part still.",
                        "Support the injury with cloth, sling, or soft padding.",
                        "Apply cold pack wrapped in cloth to reduce swelling.",
                        "Call emergency help if pain is severe or bone looks deformed.",
                        "Keep the person calm."
                ),
                Arrays.asList(
                        "Do not try to straighten the bone.",
                        "Do not move the person unnecessarily.",
                        "Do not massage the injured area."
                )));

        tips.add(new FirstAidTip(8, "Electric Shock", "Electric", "Critical", "zap",
                "For injury caused by electricity.",
                Arrays.asList("electric shock", "current", "electricity", "wire"),
                Arrays.asList(
                        "Do not touch the person until power is off.",
                        "Turn off electricity from the main switch if safe.",
                        "Call Rescue 1122.",
                        "Check breathing.",
                        "Start CPR if the person is not breathing normally.",
                        "Treat burns with clean covering."
                ),
                Arrays.asList(
                        "Do not touch the person with bare hands while electricity is active.",
                        "Do not use metal objects to move wires.",
                        "Do not ignore small electric burns."
                )));

        tips.add(new FirstAidTip(9, "Snake Bite", "Bites", "Critical", "bug",
                "For suspected snake bite.",
                Arrays.asList("snake bite", "bite", "venom", "poison"),
                Arrays.asList(
                        "Keep the person calm and still.",
                        "Call emergency help immediately.",
                        "Keep the bitten area below heart level if possible.",
                        "Remove rings, watches, or tight clothing near the bite.",
                        "Take the person to hospital as soon as possible."
                ),
                Arrays.asList(
                        "Do not cut the bite area.",
                        "Do not suck out venom.",
                        "Do not apply ice.",
                        "Do not tie a very tight tourniquet."
                )));

        tips.add(new FirstAidTip(10, "Heat Stroke", "Heat", "Critical", "sun",
                "For high body temperature, confusion, or fainting in heat.",
                Arrays.asList("heat stroke", "heat", "sun", "dehydration", "high temperature"),
                Arrays.asList(
                        "Call emergency help immediately.",
                        "Move the person to a cool shaded place.",
                        "Remove extra clothing.",
                        "Cool the person with wet cloths or water.",
                        "Fan the person if possible.",
                        "Give small sips of water only if fully awake."
                ),
                Arrays.asList(
                        "Do not give drinks if the person is unconscious or confused.",
                        "Do not leave the person alone.",
                        "Do not delay cooling."
                )));

        tips.add(new FirstAidTip(11, "Drowning", "Water", "Critical", "waves",
                "For a person rescued from water.",
                Arrays.asList("drowning", "water", "not breathing", "swimming"),
                Arrays.asList(
                        "Call Rescue 1122.",
                        "Remove the person from water only if safe.",
                        "Check breathing.",
                        "If not breathing normally, start CPR.",
                        "Keep the person warm.",
                        "Place in recovery position if breathing."
                ),
                Arrays.asList(
                        "Do not enter dangerous water without safety.",
                        "Do not shake the person to remove water.",
                        "Do not delay CPR if not breathing."
                )));

        tips.add(new FirstAidTip(12, "Earthquake Injury", "Disaster", "Serious", "activity",
                "For injuries after earthquake or building damage.",
                Arrays.asList("earthquake", "disaster", "building", "injury", "crush"),
                Arrays.asList(
                        "Move to a safe area if possible.",
                        "Check for bleeding, fractures, and breathing problems.",
                        "Apply pressure to bleeding wounds.",
                        "Do not move seriously injured people unless there is danger.",
                        "Call emergency help.",
                        "Keep the person warm and calm."
                ),
                Arrays.asList(
                        "Do not move someone with possible neck or spine injury unless necessary.",
                        "Do not enter damaged buildings.",
                        "Do not give food or water to seriously injured people."
                )));

        return tips;
    }

    public static FirstAidTip findById(int id) {
        for (FirstAidTip tip : getTips()) {
            if (tip.getId() == id) {
                return tip;
            }
        }
        return null;
    }

    public static int resolveIconRes(String iconName) {
        if (iconName == null) return R.drawable.ic_firstaid;
        switch (iconName) {
            case "flame":
                return R.drawable.ic_fire;
            case "heart-pulse":
            case "heart":
                return R.drawable.ic_emergency_numbers;
            case "zap":
                return R.drawable.ic_notification_empty;
            case "waves":
                return R.drawable.ic_location;
            case "droplet":
                return R.drawable.ic_location;
            case "person-standing":
            case "user-x":
                return R.drawable.ic_profile_avatar;
            case "bone":
                return R.drawable.ic_hospital;
            case "bug":
                return R.drawable.ic_notification_empty;
            case "sun":
                return R.drawable.ic_notification_empty;
            case "activity":
                return R.drawable.ic_menu_landslide;
            default:
                return R.drawable.ic_firstaid;
        }
    }
}

