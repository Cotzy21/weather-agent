// Stylised contour map. It is decorative and does not represent a navigable route.
const contours = [
  'M230 118C254 84 309 66 353 83S435 103 449 126 425 166 382 166 349 210 297 184 213 165 230 118Z',
  'M211 112C247 69 303 49 356 68S449 86 468 123 441 182 387 182 348 228 288 201 184 158 211 112Z',
  'M195 100C231 57 297 33 362 54S464 68 486 115 453 199 395 199 350 245 281 216 165 152 195 100Z',
  'M178 93C218 43 290 16 366 38S481 55 507 111 471 216 405 215 351 266 272 234 143 148 178 93Z',
  'M161 82C201 30 279-1 367 22S496 38 523 102 487 230 412 232 352 286 263 252 120 142 161 82Z',
  'M143 72C185 13 269-20 372 5S512 22 541 97 503 245 420 251 352 307 256 270 99 134 143 72Z',
  'M124 63C169-1 262-37 375-12S531 6 558 90 517 263 428 268 356 326 246 287 75 128 124 63Z',
  'M105 54C152-18 247-55 377-29S550-10 578 80 535 277 437 284 357 348 238 306 55 122 105 54Z',
  'M86 46C137-35 237-73 383-45S566-26 594 74 551 296 444 301 355 370 230 324 33 118 86 46Z',
]

export default function TerrainMasthead({ title, description, children, action }) {
  return (
    <section className="terrain-masthead">
      <svg className="terrain-art" viewBox="0 0 610 250" aria-hidden="true" focusable="false">
        <g className="terrain-contours" fill="none">
          {contours.map((path) => <path key={path} d={path} />)}
        </g>
        <path className="terrain-track" pathLength="1" d="M85 230C132 197 181 229 220 199S255 163 291 167 319 138 353 137 385 148 405 113" />
        <circle className="terrain-marker" cx="405" cy="113" r="4" />
      </svg>
      <div className="terrain-copy">
        <h1>{title}</h1>
        {description && <p>{description}</p>}
        {children}
      </div>
      {action && <div className="terrain-toolbar">{action}</div>}
    </section>
  )
}
