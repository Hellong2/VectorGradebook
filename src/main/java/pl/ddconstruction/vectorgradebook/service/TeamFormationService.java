package pl.ddconstruction.vectorgradebook.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.ddconstruction.vectorgradebook.model.CourseConfig;
import pl.ddconstruction.vectorgradebook.model.entity.Student;
import pl.ddconstruction.vectorgradebook.ui.TeamGeneratorView;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeamFormationService {

    private final CourseService courseService;

    public List<TeamGeneratorView.Team> generateTeams(List<Student> students, boolean preferTrios, UUID courseId) {
        if (students.isEmpty())
            return Collections.emptyList();

        CourseConfig config = courseService.getCourseConfig(courseId);
        if (config == null)
            return Collections.emptyList();

        List<String> sortedSkills = new ArrayList<>(config.getAvailableSkills());
        log.debug("Skills count: {}", sortedSkills.size());

        Map<UUID, double[]> studentVectors = new HashMap<>();

        for (Student s : students) {
            studentVectors.put(s.getId(), calculateStudentVector(s, sortedSkills, config));
        }

        List<Student> sortedStudents = new ArrayList<>(students);
        sortedStudents.sort(Comparator.comparingDouble(s -> calculateAverage(s, studentVectors.get(s.getId()))));

        Set<UUID> assigned = new HashSet<>();
        List<TeamGeneratorView.Team> teams = new ArrayList<>();

        for (Student student : sortedStudents) {
            if (assigned.contains(student.getId()))
                continue;

            double[] vecA = studentVectors.get(student.getId());
            Student partner = findBestPartner(student, vecA, sortedStudents, assigned, studentVectors);

            if (partner != null) {
                List<Student> teamMembers = new ArrayList<>();
                teamMembers.add(student);
                teamMembers.add(partner);

                double[] currentTeamVec = calculateMaxVector(vecA, studentVectors.get(partner.getId()));

                if (preferTrios) {
                    Student third = findThirdPartner(teamMembers, currentTeamVec, sortedStudents, assigned,
                            studentVectors);
                    if (third != null) {
                        teamMembers.add(third);
                        assigned.add(third.getId());
                    }
                }

                assigned.add(student.getId());
                assigned.add(partner.getId());

                double score = calculateSynergy(teamMembers, studentVectors);
                teams.add(new TeamGeneratorView.Team(teamMembers, score));
            }
        }

        List<Student> leftovers = students.stream().filter(s -> !assigned.contains(s.getId())).toList();
        for (Student left : leftovers) {
            boolean added = false;
            for (TeamGeneratorView.Team team : teams) {
                if (team.getMembers().size() < 3) {
                    team.getMembers().add(left);
                    team.setScore(calculateSynergy(team.getMembers(), studentVectors));
                    assigned.add(left.getId());
                    added = true;
                    break;
                }
            }
            if (!added) {
                teams.add(new TeamGeneratorView.Team(new ArrayList<>(List.of(left)),
                        calculateAverage(left, studentVectors.get(left.getId()))));
            }
        }

        teams.sort(Comparator.comparingDouble(TeamGeneratorView.Team::getScore).reversed());

        return teams;
    }

    private Student findBestPartner(Student candidate, double[] vecA, List<Student> pool, Set<UUID> assigned,
            Map<UUID, double[]> vectors) {
        Student bestPartner = null;
        double bestSynergy = -1.0;

        for (Student other : pool) {
            if (other.getId().equals(candidate.getId()) || assigned.contains(other.getId()))
                continue;

            double[] vecB = vectors.get(other.getId());
            double synergy = calculateSynergyOfVectors(vecA, vecB);

            if (synergy > bestSynergy) {
                bestSynergy = synergy;
                bestPartner = other;
            }
        }
        return bestPartner;
    }

    private Student findThirdPartner(List<Student> team, double[] teamVec, List<Student> pool, Set<UUID> assigned,
            Map<UUID, double[]> vectors) {
        Student bestThird = null;
        double bestNewSynergy = -1.0;
        double currentSynergy = calculateMean(teamVec);

        for (Student other : pool) {
            if (assigned.contains(other.getId()) || team.stream().anyMatch(m -> m.getId().equals(other.getId())))
                continue;

            double[] vecC = vectors.get(other.getId());
            double[] newTeamVec = calculateMaxVector(teamVec, vecC);
            double newSynergy = calculateMean(newTeamVec);

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
            if (cls.skills().stream().anyMatch(s -> s.name().equals(skill))) {
                Double g = student.getClassGrades().get(cls.id());
                if (g != null)
                    grades.add(g);
            }
        });
        return grades.stream().mapToDouble(d -> d).average().orElse(0.0);
    }

    private double calculateSynergy(List<Student> members, Map<UUID, double[]> vectors) {
        if (members.isEmpty())
            return 0.0;
        double[] maxVec = vectors.get(members.getFirst().getId()).clone();

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
        if (vector.length == 0)
            return 3.0;
        double sum = 0;
        for (double v : vector)
            sum += v;
        return sum / vector.length;
    }

    private double calculateMean(double[] vector) {
        if (vector.length == 0)
            return 0;
        double sum = 0;
        for (double v : vector)
            sum += v;
        return sum / vector.length;
    }
}
