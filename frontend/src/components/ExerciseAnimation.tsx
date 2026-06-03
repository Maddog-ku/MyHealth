import { cn } from "@/lib/utils";

/**
 * Local-first exercise guidance: a stick figure animated to match the *movement*,
 * not just the muscle group. The figure is drawn in the correct posture for each
 * pattern — a floor move lies flat, a plank holds prone, a squat stands — so the
 * guidance reads accurately. Pure inline SVG + CSS keyframes (see styles.css,
 * `.wk-figure*`), honouring `prefers-reduced-motion`. No assets, no network.
 *
 * `phase === "work"` plays the motion; `"rest"` (also used while paused) freezes
 * into a calm idle so the user can tell at a glance whether to move or recover.
 */

type Pattern =
  | "crunch" | "deadbug" | "plank" | "sidePlank" | "climber" | "pushup"
  | "squat" | "lunge" | "bridge" | "jack" | "highKnees" | "burpee"
  | "twist" | "sideBend" | "curl" | "superman" | "generic";

/** Match on the exercise name first (most specific), then fall back by category. */
function resolvePattern(name: string, category: string): Pattern {
  const n = name ?? "";
  const has = (...keys: string[]) => keys.some((k) => n.includes(k));

  if (has("側棒", "側平板")) return "sidePlank";
  if (has("棒式", "平板", "撐體")) return "plank";
  if (has("登山")) return "climber";
  if (has("伏地", "俯臥撐", "推胸", "胸推", "臥推")) return "pushup";
  if (has("捲腹", "卷腹", "仰臥起坐", "仰臥捲", "捲體")) return "crunch";
  if (has("死蟲", "鳥狗")) return "deadbug";
  if (has("臀橋", "橋式", "臀推", "橋")) return "bridge";
  if (has("深蹲", "蹲")) return "squat";
  if (has("弓箭步", "箭步", "分腿蹲", "跨步")) return "lunge";
  if (has("開合跳", "開合")) return "jack";
  if (has("高抬腿", "抬腿", "原地跑", "踏步")) return "highKnees";
  if (has("波比")) return "burpee";
  if (has("俄羅斯", "轉體", "轉腰")) return "twist";
  if (has("側屈", "側彎", "側傾")) return "sideBend";
  if (has("彎舉", "二頭", "捲舉", "臂屈伸", "划船")) return "curl";
  if (has("超人", "背伸", "燕式")) return "superman";

  switch (category) {
    case "abs": return "crunch";
    case "waist": return "twist";
    case "legs": return "squat";
    case "glutes": return "bridge";
    case "chest": return "pushup";
    case "back": return "superman";
    case "arms": return "curl";
    case "cardio": return "jack";
    case "full_body": return "burpee";
    default: return "generic";
  }
}

export function ExerciseAnimation({
  name,
  category,
  phase,
  className,
}: {
  name: string;
  category: string;
  phase: "work" | "rest";
  className?: string;
}) {
  const pattern = resolvePattern(name, category);

  return (
    <svg
      viewBox="0 0 120 120"
      role="img"
      aria-label={phase === "work" ? `${name} 動作示範` : "休息中"}
      className={cn("wk-figure", `wk-figure--${pattern}`, phase === "rest" && "wk-figure--rest", className)}
    >
      <line x1="14" y1="100" x2="106" y2="100" className="wk-ground" />
      <g className="wk-fig">{SCENES[pattern]}</g>
    </svg>
  );
}

// --- Scene primitives ------------------------------------------------------

function Bone({ x1, y1, x2, y2, cls }: { x1: number; y1: number; x2: number; y2: number; cls?: string }) {
  return <line x1={x1} y1={y1} x2={x2} y2={y2} className={cn("wk-bone", cls)} />;
}
function Head({ cx, cy, cls }: { cx: number; cy: number; cls?: string }) {
  return <circle cx={cx} cy={cy} r="11" className={cn("wk-head", cls)} />;
}

// A standing figure shared by upright patterns. `.up` (torso+head+arms) pivots at
// the hips; legs are jointed (thigh + shin) so squats/lunges can bend the knees.
const standing = (
  <>
    <g className="hips">
      <g className="legL">
        <Bone x1={60} y1={63} x2={51} y2={82} />
        <g className="shinL"><Bone x1={51} y1={82} x2={50} y2={100} /></g>
      </g>
      <g className="legR">
        <Bone x1={60} y1={63} x2={69} y2={82} />
        <g className="shinR"><Bone x1={69} y1={82} x2={70} y2={100} /></g>
      </g>
      <g className="up">
        <Bone x1={60} y1={31} x2={60} y2={63} />
        <Head cx={60} cy={20} />
        <g className="armL">
          <Bone x1={60} y1={34} x2={48} y2={48} />
          <g className="foreL"><Bone x1={48} y1={48} x2={46} y2={63} /></g>
        </g>
        <g className="armR">
          <Bone x1={60} y1={34} x2={72} y2={48} />
          <g className="foreR"><Bone x1={72} y1={48} x2={74} y2={63} /></g>
        </g>
      </g>
    </g>
  </>
);

const SCENES: Record<Pattern, JSX.Element> = {
  squat: standing,
  lunge: standing,
  jack: standing,
  highKnees: standing,
  sideBend: standing,
  curl: standing,
  generic: standing,
  burpee: standing,

  // Supine: lying on the back, knees bent up, head/shoulders curl toward knees.
  crunch: (
    <>
      <g className="cr-leg">
        <Bone x1={64} y1={92} x2={48} y2={76} />
        <Bone x1={48} y1={76} x2={42} y2={92} />
      </g>
      <g className="cr-up">
        <Bone x1={64} y1={92} x2={86} y2={92} />
        <Head cx={94} cy={90} />
        <Bone x1={80} y1={92} x2={72} y2={84} />
      </g>
    </>
  ),

  // Supine, hips/knees in tabletop, opposite limbs extend and return.
  deadbug: (
    <>
      <Bone x1={36} y1={92} x2={70} y2={92} />
      <Head cx={30} cy={90} />
      <g className="db-armL"><Bone x1={48} y1={92} x2={46} y2={70} /></g>
      <g className="db-armR"><Bone x1={58} y1={92} x2={60} y2={70} /></g>
      <g className="db-legL">
        <Bone x1={70} y1={92} x2={74} y2={72} />
        <Bone x1={74} y1={72} x2={86} y2={70} />
      </g>
      <g className="db-legR">
        <Bone x1={70} y1={92} x2={66} y2={72} />
        <Bone x1={66} y1={72} x2={54} y2={70} />
      </g>
    </>
  ),

  // Prone hold on a forearm; body a straight line from head to heels.
  plank: (
    <g className="pl-body">
      <Head cx={40} cy={74} />
      <Bone x1={48} y1={77} x2={96} y2={97} />
      <Bone x1={56} y1={80} x2={52} y2={100} />
      <Bone x1={52} y1={100} x2={64} y2={100} />
    </g>
  ),

  // Side hold: body a straight diagonal, bottom forearm to the floor.
  sidePlank: (
    <g className="sp-body">
      <Head cx={48} cy={66} />
      <Bone x1={55} y1={72} x2={92} y2={98} />
      <Bone x1={55} y1={72} x2={56} y2={100} />
      <Bone x1={62} y1={80} x2={60} y2={58} />
    </g>
  ),

  // Prone plank, hands planted, knees drive toward chest alternately.
  climber: (
    <>
      <g className="cl-body">
        <Head cx={42} cy={72} />
        <Bone x1={50} y1={75} x2={76} y2={86} />
        <Bone x1={52} y1={77} x2={50} y2={100} />
        <Bone x1={58} y1={79} x2={58} y2={100} />
      </g>
      <g className="cl-legR"><Bone x1={76} y1={86} x2={96} y2={98} /></g>
      <g className="cl-legL"><Bone x1={76} y1={86} x2={72} y2={92} /></g>
    </>
  ),

  // Prone on straight arms; whole body lowers and presses back up.
  pushup: (
    <g className="pu-body">
      <Head cx={40} cy={78} />
      <Bone x1={48} y1={80} x2={92} y2={92} />
      <Bone x1={54} y1={82} x2={52} y2={100} />
      <Bone x1={88} y1={91} x2={96} y2={100} />
    </g>
  ),

  // Supine, shoulders + feet planted, hips lift into a bridge and lower.
  bridge: (
    <>
      <Head cx={32} cy={90} />
      <g className="br-lift">
        <Bone x1={40} y1={92} x2={66} y2={92} />
        <Bone x1={66} y1={92} x2={78} y2={78} />
      </g>
      <Bone x1={78} y1={78} x2={82} y2={100} cls="br-shin" />
    </>
  ),

  // Seated V-sit, feet off the floor, hands swing across to twist the trunk.
  twist: (
    <>
      <Bone x1={60} y1={92} x2={50} y2={68} />
      <Head cx={47} cy={60} />
      <g className="tw-leg">
        <Bone x1={60} y1={92} x2={78} y2={78} />
        <Bone x1={78} y1={78} x2={90} y2={74} />
      </g>
      <g className="tw-arms"><Bone x1={52} y1={70} x2={66} y2={80} /></g>
    </>
  ),

  // Prone, arms reach forward and legs lift behind (back extension).
  superman: (
    <>
      <Head cx={34} cy={88} />
      <Bone x1={42} y1={90} x2={74} y2={90} />
      <g className="sm-arm"><Bone x1={44} y1={90} x2={26} y2={84} /></g>
      <g className="sm-leg"><Bone x1={74} y1={90} x2={92} y2={84} /></g>
    </>
  ),
};
