package com.datagami.edudron.content.psychtest.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class MappingService {
    public record MappingOutput(
        String streamSuggestion,
        List<String> careerFields
    ) {}

    public record CareerSuggestion(
        String title,
        String reason
    ) {}

    public record MappingExplanation(
        String streamSuggestion,
        String streamReason,
        List<CareerSuggestion> primaryCareerPaths,
        List<CareerSuggestion> alternateCareerPaths,
        List<String> roleModelsInAlignedFields
    ) {}

    public MappingOutput map(String top1, String top2, String overallConfidence, Integer grade) {
        String stream = suggestStream(top1, top2, grade);
        List<String> careers = suggestCareerFields(top1, top2, stream, overallConfidence);
        return new MappingOutput(stream, careers);
    }

    public MappingExplanation mapWithRationale(String top1, String top2, String overallConfidence, Integer grade) {
        String stream = suggestStream(top1, top2, grade);
        String streamReason = streamReason(top1, top2, stream, overallConfidence, grade);

        List<CareerSuggestion> primary = primaryCareerPaths(top1, top2, stream, overallConfidence);
        List<CareerSuggestion> alternate = alternateCareerPaths(top1, top2, stream, overallConfidence);

        // Motivation: examples of notable people in aligned fields (not claiming their psych profile).
        List<String> roleModels = roleModels(top1, top2, stream);

        return new MappingExplanation(stream, streamReason, primary, alternate, roleModels);
    }

    private String suggestStream(String top1, String top2, Integer grade) {
        // Simple rule-based mapping intended for grades 8–12
        int g = grade != null ? grade : 10;
        int scienceScore = 0;
        int commerceScore = 0;
        int artsScore = 0;

        for (String d : List.of(top1, top2)) {
            if (d == null) continue;
            switch (d) {
                case "I", "R" -> scienceScore += 2;
                case "E", "C" -> commerceScore += 2;
                case "A", "S" -> artsScore += 2;
                default -> {}
            }
        }

        // Younger grades: favor broad streams
        if (g <= 10) {
            if (scienceScore >= commerceScore && scienceScore >= artsScore) return "Science";
            if (commerceScore >= scienceScore && commerceScore >= artsScore) return "Commerce";
            return "Arts";
        }

        // Older grades: still keep stream categories (can be refined later)
        if (scienceScore >= commerceScore && scienceScore >= artsScore) return "Science";
        if (commerceScore >= scienceScore && commerceScore >= artsScore) return "Commerce";
        return "Arts";
    }

    private List<String> suggestCareerFields(String top1, String top2, String stream, String overallConfidence) {
        Set<String> fields = new LinkedHashSet<>();

        // Use combined signals first
        String combo = (top1 != null ? top1 : "") + (top2 != null ? top2 : "");
        if (combo.contains("I") && combo.contains("R")) {
            fields.add("Engineering & Technology");
            fields.add("Applied Sciences");
        }
        if (combo.contains("I") && combo.contains("C")) {
            fields.add("Data & Analytics");
            fields.add("Research & Systems");
        }
        if (combo.contains("A") && combo.contains("S")) {
            fields.add("Design & Communication");
            fields.add("Psychology & Education");
        }
        if (combo.contains("E") && combo.contains("C")) {
            fields.add("Business & Finance");
            fields.add("Operations & Management");
        }
        if (combo.contains("S") && combo.contains("E")) {
            fields.add("People Management");
            fields.add("Marketing & Public Relations");
        }

        // Stream-based defaults
        if (fields.isEmpty()) {
            if ("Science".equalsIgnoreCase(stream)) {
                fields.add("Science & Technology");
                fields.add("Engineering");
            } else if ("Commerce".equalsIgnoreCase(stream)) {
                fields.add("Business");
                fields.add("Finance");
            } else {
                fields.add("Humanities");
                fields.add("Creative Arts");
            }
        }

        // Confidence-aware third suggestion
        if ("LOW".equalsIgnoreCase(overallConfidence)) {
            fields.add("Exploration (try short projects across interests)");
        } else {
            fields.add("Skill-building (strengthen fundamentals)");
        }

        List<String> out = new ArrayList<>(fields);
        return out.subList(0, Math.min(3, out.size()));
    }

    private String streamReason(String top1, String top2, String stream, String overallConfidence, Integer grade) {
        String t1 = top1 != null ? top1 : "?";
        String t2 = top2 != null ? top2 : "?";
        String g = grade != null ? String.valueOf(grade) : "10";

        return "We suggested " + stream + " because your top interest signals were " + t1 + " and " + t2
            + " (RIASEC), and at grade " + g + " we map those to the broad stream that best matches your interests. "
            + ("LOW".equalsIgnoreCase(overallConfidence)
                ? "Your overall confidence is LOW, so treat this as a starting point and explore adjacent fields too."
                : "Your overall confidence is " + overallConfidence + ", so this is a strong direction to explore.");
    }

    private List<CareerSuggestion> primaryCareerPaths(String top1, String top2, String stream, String overallConfidence) {
        // Keep these as high-level paths; avoid overly specific claims.
        List<CareerSuggestion> out = new ArrayList<>();
        String combo = (top1 != null ? top1 : "") + (top2 != null ? top2 : "");

        if (combo.contains("I") && combo.contains("R")) {
            out.add(new CareerSuggestion("Engineering & Technology", "Strong Investigative + Realistic signals: interest in problem-solving plus hands-on/practical work."));
            out.add(new CareerSuggestion("Applied Sciences", "Investigative interests often align with experimentation, analysis, and building evidence."));
        } else if (combo.contains("I") && combo.contains("C")) {
            out.add(new CareerSuggestion("Data & Analytics", "Investigative + Conventional often aligns with structured problem-solving, patterns, and working with data."));
            out.add(new CareerSuggestion("Research & Systems", "A mix of curiosity (I) and structure (C) suits systematic investigation and documentation."));
        } else if (combo.contains("A") && combo.contains("S")) {
            out.add(new CareerSuggestion("Design & Communication", "Artistic + Social often aligns with creative expression and understanding people."));
            out.add(new CareerSuggestion("Education & Learning", "Social interests often align with helping others learn; Artistic adds creativity in how you explain/communicate."));
        } else if (combo.contains("E") && combo.contains("C")) {
            out.add(new CareerSuggestion("Business & Finance", "Enterprising + Conventional often aligns with goals, decision-making, and structured execution."));
            out.add(new CareerSuggestion("Operations & Management", "Conventional supports process; Enterprising supports ownership and initiative."));
        } else if (combo.contains("S") && combo.contains("E")) {
            out.add(new CareerSuggestion("People Management", "Social + Enterprising often aligns with motivating people and coordinating groups."));
            out.add(new CareerSuggestion("Marketing & Public Relations", "Enterprising supports persuasion; Social supports communication and audience understanding."));
        } else {
            // Stream-based defaults
            if ("Science".equalsIgnoreCase(stream)) {
                out.add(new CareerSuggestion("Science & Technology", "Your profile leaned towards Science-oriented interests and problem-solving."));
                out.add(new CareerSuggestion("Engineering", "A practical path that combines learning concepts with application."));
            } else if ("Commerce".equalsIgnoreCase(stream)) {
                out.add(new CareerSuggestion("Business", "Commerce stream aligns with planning, execution, and outcomes."));
                out.add(new CareerSuggestion("Finance", "Finance aligns with structured decision-making and numbers-driven thinking."));
            } else {
                out.add(new CareerSuggestion("Humanities", "Arts stream aligns with people-focused and communication-oriented study paths."));
                out.add(new CareerSuggestion("Creative Arts", "Creative paths align with expression, storytelling, and design."));
            }
        }

        // Confidence-aware “how to approach”
        if ("LOW".equalsIgnoreCase(overallConfidence)) {
            out.add(new CareerSuggestion("Exploration projects", "With lower confidence, short projects across these fields can help you discover fit quickly."));
        } else {
            out.add(new CareerSuggestion("Skill-building track", "Strengthening fundamentals in your top areas can increase confidence and options."));
        }

        return out.subList(0, Math.min(3, out.size()));
    }

    private List<CareerSuggestion> alternateCareerPaths(String top1, String top2, String stream, String overallConfidence) {
        List<CareerSuggestion> out = new ArrayList<>();
        String combo = (top1 != null ? top1 : "") + (top2 != null ? top2 : "");

        // Alternate paths: adjacent fields that still respect the top interests.
        if (combo.contains("I")) {
            out.add(new CareerSuggestion("Product / UX Research", "Investigative strengths can apply to understanding problems and testing solutions with users."));
        }
        if (combo.contains("A")) {
            out.add(new CareerSuggestion("Content & Media", "Artistic strengths can apply to storytelling, design, and creative production."));
        }
        if (combo.contains("S")) {
            out.add(new CareerSuggestion("Counselling / Mentoring (non-clinical)", "Social strengths can apply to guidance, coaching, and peer support roles."));
        }
        if (combo.contains("E")) {
            out.add(new CareerSuggestion("Sales / Partnerships", "Enterprising strengths can apply to pitching, negotiation, and relationship-building."));
        }
        if (combo.contains("C")) {
            out.add(new CareerSuggestion("Quality / Compliance / Coordination", "Conventional strengths can apply to accuracy, consistency, and process improvement."));
        }
        if (combo.contains("R")) {
            out.add(new CareerSuggestion("Technical Trades / Maker Projects", "Realistic strengths can apply to building, fixing, prototyping, and practical problem-solving."));
        }

        if (out.isEmpty()) {
            out.add(new CareerSuggestion("Interdisciplinary pathways", "Many careers combine interests; try mixing your top areas through projects and electives."));
        }

        // Keep concise
        return out.subList(0, Math.min(4, out.size()));
    }

    private List<String> roleModels(String top1, String top2, String stream) {
        String combo = (top1 != null ? top1 : "") + (top2 != null ? top2 : "");
        List<String> out = new ArrayList<>();

        // These are “people in aligned fields”, not “people with your exact profile”.
        if (combo.contains("I") && combo.contains("R")) {
            out.add("A.P.J. Abdul Kalam (aerospace/engineering)");
            out.add("Nikola Tesla (engineering/invention)");
            out.add("Kalpana Chawla (aerospace)");
        } else if (combo.contains("I") && combo.contains("C")) {
            out.add("Alan Turing (computing)");
            out.add("Katherine Johnson (mathematics/space programs)");
            out.add("Sundar Pichai (technology leadership)");
        } else if (combo.contains("A") && combo.contains("S")) {
            out.add("Walt Disney (creative industries)");
            out.add("A.R. Rahman (music/creative production)");
            out.add("Malala Yousafzai (education/advocacy)");
        } else if (combo.contains("E") && combo.contains("C")) {
            out.add("Indra Nooyi (business leadership)");
            out.add("Ratan Tata (business/industry)");
            out.add("Warren Buffett (investing)");
        } else if (combo.contains("S") && combo.contains("E")) {
            out.add("Oprah Winfrey (media/leadership)");
            out.add("Satya Nadella (leadership)");
            out.add("Michelle Obama (public service)");
        } else {
            if ("Science".equalsIgnoreCase(stream)) {
                out.add("Marie Curie (science)");
                out.add("C.V. Raman (science)");
                out.add("Homi Bhabha (science)");
            } else if ("Commerce".equalsIgnoreCase(stream)) {
                out.add("Narayana Murthy (entrepreneurship)");
                out.add("Dhirubhai Ambani (entrepreneurship)");
                out.add("Azim Premji (business)");
            } else {
                out.add("Rabindranath Tagore (arts/literature)");
                out.add("Frida Kahlo (art)");
                out.add("M.F. Husain (art)");
            }
        }

        out.add("Note: these are role models in related fields, not a claim about anyone’s psychometric results.");
        return out;
    }
}

