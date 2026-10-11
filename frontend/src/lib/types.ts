export type Role = "STUDENT" | "INSTRUCTOR" | "ADMIN";

export interface User {
  id: string;
  email: string;
  fullName: string;
  role: Role;
  status: string;
  avatarUrl?: string | null;
  bio?: string | null;
  headline?: string | null;
  experience?: string | null;
  interests: string[];
  skills: string[];
  learningGoals: string[];
  preferredCategoryIds: string[];
  qualifications: string[];
  portfolioLinks: { label: string; url: string }[];
  createdAt?: string;
  lastLoginAt?: string | null;
  seedData?: boolean;
}

export interface Category {
  id: string;
  name: string;
  slug: string;
  domain: "TECHNICAL" | "NON_TECHNICAL";
  parentId?: string | null;
  description?: string;
  active: boolean;
  courseCount: number;
}

export interface CourseCard {
  id: string;
  slug: string;
  title: string;
  shortDescription: string;
  instructorId: string;
  instructorName: string;
  thumbnailUrl?: string | null;
  domain: string;
  difficulty: string;
  language: string;
  categoryId: string;
  categoryName: string;
  estimatedDurationMinutes: number;
  lessonCount: number;
  enrollmentCount: number;
  averageRating?: number | null;
  reviewCount: number;
  status: string;
  progressPercent?: number | null;
  enrolled: boolean;
  seedData: boolean;
  contentDisabled: boolean;
}

export interface Lesson {
  lessonId: string;
  title: string;
  description?: string;
  objectives: string[];
  order: number;
  estimatedDurationMinutes: number;
  access: "FREE_PREVIEW" | "ENROLLED_ONLY";
  status: string;
  videoStatus: string;
  videoDurationSeconds?: number | null;
  resources: { title: string; url: string; type: string }[];
}

export interface Section {
  sectionId: string;
  title: string;
  description?: string;
  order: number;
  status: string;
  lessons: Lesson[];
}

export interface CourseDetail extends CourseCard {
  description: string;
  learningObjectives: string[];
  prerequisites: string[];
  targetAudience: string[];
  tags: string[];
  skills: string[];
  rejectionReason?: string | null;
  disabledReason?: string | null;
  sections: Section[];
  instructor: { id: string; fullName: string; headline?: string | null; avatarUrl?: string | null };
  viewer: {
    enrolled: boolean;
    progressPercent?: number | null;
    lastLessonId?: string | null;
    lastLessonTitle?: string | null;
    lastPositionSeconds?: number | null;
    completedLessonCount: number;
    countableLessonCount: number;
    enrollmentStatus?: string | null;
  };
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
}

export interface PublicConfig {
  seedEnabled: boolean;
  aiConfigured: boolean;
  platformName: string;
  replicaSet: boolean;
}
