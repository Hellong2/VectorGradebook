package pl.ddconstruction.vectorgradebook.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.ddconstruction.vectorgradebook.model.Class;
import pl.ddconstruction.vectorgradebook.model.CourseConfig;
import pl.ddconstruction.vectorgradebook.model.Student;
import pl.ddconstruction.vectorgradebook.ui.TeamGeneratorView;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeamFormationService {

    private final CourseService courseService;

    public List<TeamGeneratorView.Team> generateTeams(List<Student> students, boolean preferTrios) {
        if (students.isEmpty())
            return Collections.emptyList();

        CourseConfig config = courseService.getCurrentConfig();
        if (config == null)
            return Collections.emptyList();

        // 1. Prepare Data
        // Skills are now the dimensions
        List<String> sortedSkills = new ArrayList<>(config.getAvailableSkills());
        int skillCount = sortedSkills.size();

        // Pre-calculate vectors for all students
        // Map<StudentID, float[]>
        Map<UUID, double[]> studentVectors = new HashMap<>();
        Map<Integer, Student> indexMap = new HashMap<>(); // For testing if needed, or debugging
        
        for (Student s : students) {
            studentVectors.put(s.getId(), calculateStudentVector(s, sortedSkills, config));
        }

        // 2. Greedy Optimization
        // Sort weakest first
        List<Student> sortedStudents = new ArrayList<>(students);
        sortedStudents.sort(Comparator.comparingDouble(s -> calculateAverage(s, studentVectors.get(s.getId()))));

        Set<UUID> assigned = new HashSet<>();
        List<TeamGeneratorView.Team> teams = new ArrayList<>();

        for (Student student : sortedStudents) {
            if (assigned.contains(student.getId()))
                continue;

            // Find best partner
            double[] vecA = studentVectors.get(student.getId());
            Student partner = findBestPartner(student, vecA, sortedStudents, assigned, studentVectors);

            if (partner != null) {
                List<Student> teamMembers = new ArrayList<>();
                teamMembers.add(student);
                teamMembers.add(partner);
                
                double[] currentTeamVec = calculateMaxVector(vecA, studentVectors.get(partner.getId()));
                
                // If preferTrios, try to find a 3rd
                if (preferTrios) {
                     Student third = findThirdPartner(teamMembers, currentTeamVec, sortedStudents, assigned, studentVectors);
                     if (third != null) {
                        teamMembers.add(third);
                        assigned.add(third.getId());
                     }
                }

                assigned.add(student.getId());
                assigned.add(partner.getId());
                
                double score = calculateSynergy(teamMembers, studentVectors);
                teams.add(new TeamGeneratorView.Team(teamMembers, score));
            } else {
                // No partner found (unlikely unless singleton logic needed)
                // For now, maybe stash as leftover or form singleton (bad score)
                // We will handle leftovers at end if needed, but for now loop continues.
            }
        }

        // Handle leftovers (merge into pairs if preferTrios was false originally or couldn't find 3rd)
        // Or if simple pair finding left someone out.
        // Current logic skips if no partner found.
        List<Student> leftovers = students.stream().filter(s -> !assigned.contains(s.getId())).toList();
        for (Student left : leftovers) {
             // Try to add to existing team (max 3)
             boolean added = false;
             for (TeamGeneratorView.Team team : teams) {
                 if (team.getMembers().size() < 3) {
                     // Check if synergy improves or at least stays > 3?
                     // Just add to first available for now to ensure coverage
                     team.getMembers().add(left);
                     // Recalculate score
                     team.setScore(calculateSynergy(team.getMembers(), studentVectors));
                     assigned.add(left.getId());
                     added = true;
                     break;
                 }
             }
             if (!added) {
                 // Create singleton team
                 teams.add(new TeamGeneratorView.Team(new ArrayList<>(List.of(left)), 
                        calculateAverage(left, studentVectors.get(left.getId()))));
             }
        }

        // 3. Sort by score descending
        teams.sort(Comparator.comparingDouble(TeamGeneratorView.Team::getScore).reversed());

        return teams;
    }

    private Student findBestPartner(Student candidate, double[] vecA, List<Student> pool, Set<UUID> assigned, Map<UUID, double[]> vectors) {
        Student bestPartner = null;
        double bestSynergy = -1.0;

        // Gap vector: We want someone who is strong where A is weak.
        // But the metric is Synergy = Mean(Max(A, B)).
        // We simply maximize Synergy.
        
        for (Student other : pool) {
            if (other.getId().equals(candidate.getId()) || assigned.contains(other.getId()))
                continue;

            double[] vecB = vectors.get(other.getId());
            double synergy = calculateSynergyOfVectors(vecA, vecB);

            // Constraint: Avoid score below 3.0
            if (synergy < 3.0) {
                 // If we have no better option, we might have to take it?
                 // Let's implement soft preference: only pick < 3.0 if no choice.
                 // Actually, bestSynergy starts at -1. So if all are < 3.0, we pick the highest < 3.0.
            }

            if (synergy > bestSynergy) {
                bestSynergy = synergy;
                bestPartner = other;
            }
        }
        return bestPartner;
    }

    private Student findThirdPartner(List<Student> team, double[] teamVec, List<Student> pool, Set<UUID> assigned, Map<UUID, double[]> vectors) {
        Student bestThird = null;
        double bestNewSynergy = -1.0;
        double currentSynergy = calculateMean(teamVec);

        for (Student other : pool) {
            if (assigned.contains(other.getId()) || team.stream().anyMatch(m -> m.getId().equals(other.getId())))
                continue;

            double[] vecC = vectors.get(other.getId());
            double[] newTeamVec = calculateMaxVector(teamVec, vecC);
            double newSynergy = calculateMean(newTeamVec);

            // We want to improve synergy or at least keep it high?
            // "avoid score below 3".
            if (newSynergy > currentSynergy || newSynergy > 3.0) {
                 if (newSynergy > bestNewSynergy) {
                     bestNewSynergy = newSynergy;
                     bestThird = other;
                 }
            }
        }
        return bestThird;
    }

    private double[] calculateStudentVector(Student student, List<String> skills, CourseConfig config) {
        double[] vector = new double[skills.size()];
        for (int i = 0; i < skills.size(); i++) {
            String skill = skills.get(i);
            vector[i] = calculateStudentSkillAverage(student, skill, config);
        }
        return vector;
    }

    private double calculateStudentSkillAverage(Student student, String skill, CourseConfig config) {
        List<Double> grades = new ArrayList<>();
        config.getClasses().forEach(cls -> {
            if (cls.getSkills().contains(skill)) { // Changed from getTags
                Double g = student.getClassGrades().get(cls.getId());
                if (g != null)
                    grades.add(g);
            }
        });
        return grades.stream().mapToDouble(d -> d).average().orElse(0.0);
    }

    private double calculateSynergy(List<Student> members, Map<UUID, double[]> vectors) {
        if (members.isEmpty()) return 0.0;
        double[] maxVec = vectors.get(members.get(0).getId()).clone();
        
        for (int i = 1; i < members.size(); i++) {
            double[] vec = vectors.get(members.get(i).getId());
            for (int j = 0; j < maxVec.length; j++) {
                maxVec[j] = Math.max(maxVec[j], vec[j]);
            }
        }
        
        return calculateMean(maxVec);
    }

    private double calculateSynergyOfVectors(double[] v1, double[] v2) {
        double sum = 0;
        for (int i = 0; i < v1.length; i++) {
            sum += Math.max(v1[i], v2[i]);
        }
        return v1.length == 0 ? 0 : sum / v1.length;
    }
    
    private double[] calculateMaxVector(double[] v1, double[] v2) {
        double[] max = new double[v1.length];
        for (int i = 0; i < v1.length; i++) {
            max[i] = Math.max(v1[i], v2[i]);
        }
        return max;
    }

    private double calculateAverage(Student s, double[] vector) {
         if (vector.length == 0) return 3.0; // Default average
         double sum = 0;
         for (double v : vector) sum += v;
         return sum / vector.length;
    }
    
    // Mean of vector components
    private double calculateMean(double[] vector) {
        if (vector.length == 0) return 0;
        double sum = 0;
        for (double v : vector) sum += v;
        return sum / vector.length;
    }
}
